package cn.sevendays.focus;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/** One atomic snapshot; a damaged database is preserved, never silently reset. */
final class PolicyDatabase extends SQLiteOpenHelper {
    PolicyDatabase(Context c){super(c,"policy_v2.db",null,1,db->{throw new SQLiteException("规则数据库损坏，保留原文件");});}
    @Override public void onConfigure(SQLiteDatabase db){db.execSQL("PRAGMA synchronous=FULL");}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE snapshot(id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL, digest TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new SQLiteException("不支持的规则数据库版本");}
    static String digest(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    String read(){try(Cursor c=getReadableDatabase().rawQuery("SELECT payload,digest FROM snapshot WHERE id=1",null)){if(!c.moveToFirst())return null;String text=c.getString(0);if(!digest(text).equals(c.getString(1)))throw new SQLiteException("规则校验失败");return text;}}
    void write(String payload){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{ContentValues values=new ContentValues();values.put("id",1);values.put("payload",payload);values.put("digest",digest(payload));db.insertWithOnConflict("snapshot",null,values,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();}finally{db.endTransaction();}}
}
