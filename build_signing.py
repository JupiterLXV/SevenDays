"""Sign Android builds with a local key; keep all private signing inputs outside Git."""
import os
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent


def jdk_tool(name):
    home = os.environ.get("JAVA_HOME")
    return pathlib.Path(home) / "bin" / (name + (".exe" if os.name == "nt" else "")) if home else name


def sign_apk(build_tools, source, output):
    path = os.environ.get("SEVENDAYS_KEYSTORE")
    if path:
        key = pathlib.Path(path).expanduser().resolve()
        if not key.is_file():
            raise SystemExit("SEVENDAYS_KEYSTORE must point to an existing local keystore.")
        if not os.environ.get("SEVENDAYS_KEYSTORE_PASSWORD"):
            raise SystemExit("Set SEVENDAYS_KEYSTORE_PASSWORD for your local signing key.")
        alias = os.environ.get("SEVENDAYS_KEY_ALIAS", "sevendays")
        password = "env:SEVENDAYS_KEYSTORE_PASSWORD"
        key_password = (["--key-pass", "env:SEVENDAYS_KEY_PASSWORD"]
                        if os.environ.get("SEVENDAYS_KEY_PASSWORD") else [])
    else:
        key = ROOT / "personal-signing" / "debug.p12"
        alias = "androiddebugkey"
        password = "pass:android"
        key_password = []
        if not key.exists():
            key.parent.mkdir(exist_ok=True)
            subprocess.run([str(jdk_tool("keytool")), "-genkeypair", "-keystore", str(key),
                            "-storetype", "PKCS12", "-alias", alias, "-storepass", "android",
                            "-keypass", "android", "-dname", "CN=SevenDays Debug",
                            "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                            "-noprompt"], check=True, cwd=ROOT)
    subprocess.run([str(jdk_tool("java")), "-jar", str(build_tools / "lib/apksigner.jar"),
                    "sign", "--ks", str(key), "--ks-key-alias", alias, "--ks-pass", password,
                    *key_password, "--out", str(output), str(source)], check=True, cwd=ROOT)
