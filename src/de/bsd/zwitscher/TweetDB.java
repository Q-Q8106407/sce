package de.bsd.zwitscher;

import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import de.bsd.zwitscher.account.Account;

/**
 * This class is interfacing with the SQLite3 database on the
 * handset to store statuses, lists, users and so on.
 *
 * @author Heiko W. Rupp
 */
public class TweetDB {

    private static final String TABLE_ACCOUNTS = "accounts";
    private static final String TABLE_STATUSES = "statuses";
    private static final String TABLE_LAST_READ = "lastRead";
    private static final String TABLE_LISTS = "lists";
    private static final String TABLE_USERS = "users";
    private static final String TABLE_SEARCHES = "searches";
    private static final String TABLE_DIRECTS = "directs";
    static final String STATUS = "STATUS";
    static final String ACCOUNT_ID = "ACCOUNT_ID";
    static final String ACCOUNT_ID_IS = ACCOUNT_ID + "=?";
    private TweetDBOpenHelper tdHelper;
    private final String account;

	public TweetDB(Context context, int accountId) {
		tdHelper = new TweetDBOpenHelper(context, "TWEET_DB", null, 1);
        account = String.valueOf(accountId);

	}


    private class TweetDBOpenHelper extends SQLiteOpenHelper {
        static final String CREATE_TABLE = "CREATE TABLE ";

		public TweetDBOpenHelper(Context context, String name,
				CursorFactory factory, int version) {
			super(context, name, factory, version);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE + TABLE_STATUSES + " (" +
                    "ID LONG, " +
                    ACCOUNT_ID + " LONG, " +
                    "LIST_ID LONG, " +
                    "I_REP_TO LONG, " +
                    "STATUS STRING " +
                    ")"
            );

            db.execSQL(CREATE_TABLE + TABLE_DIRECTS + " (" +
                    "ID LONG, " +
                    "created_at LONG, " +
                    ACCOUNT_ID + " LONG, " +
                    "MESSAGE_JSON STRING " +
                    ")"
            );

			db.execSQL(CREATE_TABLE + TABLE_LAST_READ + " (" + //
					"list_id LONG, " + //
					"last_read_id LONG, " +  // Last Id read by the user
                    "last_fetched_id LONG, " +  // last Id fetched from the server
                    ACCOUNT_ID + " LONG " +
                    ")"
			);
			db.execSQL(CREATE_TABLE + TABLE_LISTS + " (" + //
					"name TEXT, " + //
					"id LONG, " +
                    ACCOUNT_ID + " LONG, " +
                    "list_json TEXT" +
                    " )"
			);

            db.execSQL(CREATE_TABLE + TABLE_USERS + " (" +
                    "userId LONG, " + //
                    ACCOUNT_ID + " LONG, " +
                    "user_json STRING )"
            );

         db.execSQL("CREATE TABLE accounts (" +
                     "name TEXT, " + // 0
                     "tokenKey TEXT, "+ // 1
                     "tokenSecret TEXT, "+ // 2
                     "serverUrl TEXT, " + // 3
                     "serverType )" // 4
         );

            db.execSQL(CREATE_TABLE + TABLE_SEARCHES + " ("+
                    "name STRING, "+
                    "id LONG, " +
                    ACCOUNT_ID + " LONG, " +
                    "query STRING, " +
                    "json STRING )"
            );
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		}

	}

    /**
     * Return the id of the status that was last read
     * @param list_id id of the list
     * @return
     */
	long getLastRead(int list_id) {
		SQLiteDatabase db = tdHelper.getReadableDatabase();
		Cursor c = db.query(TABLE_LAST_READ, new String[] {"last_read_id"}, "list_id = ? AND " + ACCOUNT_ID_IS, new String[] {String.valueOf(list_id),account}, null, null, null);
		Long ret;
		if (c.getCount()==0)
			ret = -1L;
		else {
			c.moveToFirst();
			ret = c.getLong(0);
		}
		c.close();
		db.close();
		return ret;
	}

    /**
     * Update (or initially store) the last read information of the passed list
     * @param list_id List to mark as read
     * @param last_read_id Id of the last read status
     */
	void updateOrInsertLastRead(int list_id, long last_read_id) {
		ContentValues cv = new ContentValues();
		cv.put("list_id", list_id);
		cv.put("last_read_id", last_read_id);
        cv.put(ACCOUNT_ID,account);

		SQLiteDatabase db = tdHelper.getWritableDatabase();
		int updated = db.update(TABLE_LAST_READ, cv, "list_id = ? AND " + ACCOUNT_ID_IS, new String[] {String.valueOf(list_id),account});
		if (updated==0) {
			// row not yet present
			db.insert(TABLE_LAST_READ, null, cv);
		}
		db.close();
	}


    /**
     * Return Infos about all lists in the DB
     * @return
     * @todo return the json object
     */
	Map<String, Integer> getLists() {
		SQLiteDatabase db = tdHelper.getReadableDatabase();
		Map<String,Integer> ret = new HashMap<String,Integer>();
		Cursor c = db.query(TABLE_LISTS, new String[] {"name","id"}, ACCOUNT_ID_IS, new String[]{account}, null, null, "name");
		if (c.getCount()>0){
			c.moveToFirst();
			do {
				String name = c.getString(0);
				Integer id = c.getInt(1);
				ret.put(name, id);
			} while (c.moveToNext());
		}
		c.close();
		db.close();
		return ret;
	}

    /**
     * Add a new list to the database
     * @param name Name of the lise
     * @param id Id of the list
     * @param json Full json string object of the list
     */
	public void addList(String name, int id, String json) {
		ContentValues cv = new ContentValues();
		cv.put("name", name);
		cv.put("id",id);
        cv.put(ACCOUNT_ID,account);
        cv.put("list_json",json);

		SQLiteDatabase db = tdHelper.getWritableDatabase();
		db.insert(TABLE_LISTS, null, cv);
		db.close();

	}

    /**
     * Delete the list with the passed ID in the DB
     * @param id Id of the list to delete
     * @todo Also remove statuses for the passed list
     */
	public void removeList(Integer id) {
		SQLiteDatabase db = tdHelper.getWritableDatabase();
		db.delete(TABLE_LISTS, "id = ? AND " +ACCOUNT_ID_IS, new String[]{id.toString(),account});
		db.close();
	}

    /**
     * Store a new Status object in the DB. See {@link twitter4j.Status}
     * @param id Id of the status
     * @param i_reply_id Id of a status the passed one is a reply to
     * @param list_id Id of a list - pseudo IDs apply --see {@link de.bsd.zwitscher.TwitterHelper#getTimeline(twitter4j.Paging, int, boolean)}
     * @param status_json
     */
    public void storeStatus(long id, long i_reply_id, long list_id, String status_json) {
        ContentValues cv = new ContentValues(4);
        cv.put("ID", id);
        cv.put("I_REP_TO", i_reply_id);
        cv.put("LIST_ID", list_id);
        cv.put(ACCOUNT_ID,account);
        cv.put(STATUS,status_json);
        SQLiteDatabase db = tdHelper.getWritableDatabase();
        db.insert(TABLE_STATUSES, null, cv);
        db.close();
    }

    /**
     * Update the stored TwitterResponse object. This may be necessary when e.g. the
     * favorite status has been changed on it.
     * @param id Id of the object
     * @param status_json Json representation of it.
     */
    public void updateStatus(long id, String status_json) {
        ContentValues cv = new ContentValues(1);
        cv.put(STATUS, status_json);
        cv.put(ACCOUNT_ID,account);
        SQLiteDatabase db = tdHelper.getWritableDatabase();
        db.update(TABLE_STATUSES,cv,"id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    /**
     * Return the blob of one stored status by its id and list_id.
     * A status with the same id can occur multiple times with various
     * listIds.
     *
     * @param statusId The id of the status
     * @param listId The id of the list this status appears
     * @return The json_string if the status exists in the DB or null otherwise
     */
    public String getStatusObjectById(long statusId, Long listId) {

        SQLiteDatabase db = tdHelper.getReadableDatabase();
        String ret = null;
        Cursor c;
        String statusIdS = String.valueOf(statusId);
        if (listId!=null) {
            c= db.query(TABLE_STATUSES, // Table
                    new String[]{STATUS}, // returned column
                    "id = ? AND list_id = ? AND " + ACCOUNT_ID_IS, // selection
                    new String[]{statusIdS,String.valueOf(listId),account}, // selection param
                    null, // groupBy
                    null, // having
                    null // order by
            );
        }
        else { // We don't care here - just take one if present
            c= db.query(TABLE_STATUSES, // Table
                    new String[]{STATUS}, // returned column
                    "id = ? AND " + ACCOUNT_ID_IS, // selection
                    new String[]{statusIdS,account}, // selection param
                    null, // groupBy
                    null, // having
                    null // order by
            );

        }
        if (c.getCount()>0){
            c.moveToFirst();
            ret = c.getString(0);
        }
        c.close();
        db.close();

        return ret;
    }

    /**
     * Get all statuses that are marked as a reply to the passed one.
     * @param inRepyId Id of the original status
     * @return  List of Json_objects that represent the replies
     */
    public List<String> getReplies(long inRepyId) {
        SQLiteDatabase db = tdHelper.getReadableDatabase();
        List<String> ret = new ArrayList<String>();

        Cursor c ;
        c = db.query(TABLE_STATUSES,new String[]{STATUS}, "i_rep_to = ? & " + ACCOUNT_ID_IS
                ,new String[]{String.valueOf(inRepyId),account},null,null,"ID DESC");
        if (c.getCount()>0) {
            c.moveToFirst();
            do {
                String json = c.getString(0);
                ret.add(json);
            } while (c.moveToNext());
        }
        c.close();
        db.close();

        return ret;
    }


    /**
     * Return a list of Responses along for the passed list id.
     * @param sinceId What is the oldest status to look after
     * @param howMany How many entries shall be returned
     * @param list_id From which list?
     * @return List of JResponse objects
     */
    public List<String> getStatusesObjsOlderThan(long sinceId, int howMany, long list_id) {
        List<String> ret = new ArrayList<String>();
        SQLiteDatabase db = tdHelper.getReadableDatabase();
        Cursor c;
        String listIdS = String.valueOf(list_id);
        if (sinceId>-1)
            c = db.query(TABLE_STATUSES, // Table
                    new String[]{STATUS}, // Columns returned
                    "id < ? AND list_id = ? AND " +ACCOUNT_ID_IS, // selection
                    new String[]{String.valueOf(sinceId), listIdS,account}, // selection values
                    null, // group by
                    null, // having
                    "ID DESC", // order by
                    String.valueOf(howMany) // limit
            );
        else // since id = -1 -> just get the n newest
            c = db.query(TABLE_STATUSES, // Table
                    new String[]{STATUS}, // Columns returned
                    "list_id = ? AND " + ACCOUNT_ID_IS, // selection
                    new String[]{listIdS,account},  // selection values
                    null, // group by
                    null, // having
                    "ID DESC", // order by
                    String.valueOf(howMany) // limit
            );

        if (c.getCount()>0){
            c.moveToFirst();
            do {
                String json = c.getString(0);
                ret.add(json);
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return ret;
    }

    public List<String> getDirectsOlderThan(int sinceId, int howMany) {
        List<String> ret = new ArrayList<String>();
        SQLiteDatabase db = tdHelper.getReadableDatabase();
        Cursor c;
        if (sinceId>-1)
            c = db.query(TABLE_DIRECTS,new String[]{"MESSAGE_JSON"},"id < ? AND " +ACCOUNT_ID_IS,new String[]{String.valueOf(sinceId),account},null,null,"CREATED_AT DESC",String.valueOf(howMany));
        else
            c = db.query(TABLE_DIRECTS,new String[]{"MESSAGE_JSON"},  ACCOUNT_ID_IS,new String[]{account},null,null,"CREATED_AT DESC",String.valueOf(howMany));

        if (c.getCount()>0){
            c.moveToFirst();
            do {
                String json = c.getString(0);
                ret.add(json);
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return ret;

    }


    /**
     * Purge the last read table.
     */
    public void resetLastRead() {
        SQLiteDatabase db = tdHelper.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_LAST_READ);
        db.close();
    }

    /**
     * Purge the statuses table.
     */
    public void cleanTweets() {
        SQLiteDatabase db = tdHelper.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_STATUSES);
        db.execSQL("DELETE FROM " + TABLE_DIRECTS);
        db.execSQL("DELETE FROM " + TABLE_USERS);
        db.execSQL("DELETE FROM " + TABLE_LAST_READ);
        db.close();
    }

    /**
     * Returns a user by its ID from the database if it exists or null.
     * @param userId Id of the user
     * @param accountId Id of the account to use
     * @return Basic JSON string of the user info or null.
     */
    public String getUserById(int userId, int accountId) {

        SQLiteDatabase db = tdHelper.getReadableDatabase();
        String ret = null;

        Cursor c;
        c = db.query(TABLE_USERS,new String[]{"user_json"},"userId = ? AND  " + ACCOUNT_ID + " = ?",new String[] { String.valueOf(userId), String.valueOf(accountId)},null, null, null);
        if (c.getCount()>0) {
            c.moveToFirst();
            ret = c.getString(0);
        }
        c.close();
        db.close();
        return ret;
    }

    /**
     * Return a list of all users stored
     * @return
     */
    public List<String> getUsers() {
        SQLiteDatabase db = tdHelper.getReadableDatabase();
        List<String> ret = new ArrayList<String>();

        Cursor c;
        c = db.query(TABLE_USERS,new String[]{"user_json"}, ACCOUNT_ID_IS ,new String[] { String.valueOf(account)},null, null, null);
        if (c.getCount()>0) {
            c.moveToFirst();
            do {
                String json = c.getString(0);
                ret.add(json);
            } while (c.moveToNext());
        }
        c.close();
        db.close();

        return ret;
    }


    /**
     * Insert a user into the database.
     * @param userId The Id of the user to insert
     * @param json JSON representation of the User object
     */
    public void insertUser(int userId, String json) {
        ContentValues cv = new ContentValues(3);
        cv.put("userId",userId);
        cv.put(ACCOUNT_ID,account);
        cv.put("user_json",json);

        SQLiteDatabase db = tdHelper.getWritableDatabase();
        db.insert(TABLE_USERS,null,cv);
        db.close();
    }

    /**
     * Update an existing user in the database.
     * @param userId
     * @param json
     */
    public void updateUser(int userId, String json) {
        ContentValues cv = new ContentValues(1);
        cv.put("user_json",json);

        SQLiteDatabase db = tdHelper.getWritableDatabase();
        db.update(TABLE_USERS,cv,"userId = ? AND "+ ACCOUNT_ID + " = ?",new String[] { String.valueOf(userId),account});
        db.close();
    }

    public Account getAccount(String name,String type) {
        SQLiteDatabase db = tdHelper.getReadableDatabase();
        Cursor c;
        Account account=null;
        c = db.query(TABLE_ACCOUNTS,null,"name = ? AND serverType = ?", new String[]{name,type},null,null,null);
        if (c.getColumnCount()>0) {
            c.moveToFirst();
            account = new Account(
                    name,
                    c.getString(1),
                    c.getString(2),
                    type,
                    c.getString(4)
            );
        }
        c.close();
        db.close();
        return account;
    }

    public void deleteAccount(Account account) {
        SQLiteDatabase db = tdHelper.getWritableDatabase();
        db.delete(TABLE_ACCOUNTS,"name = ? AND type = ? ", new String[]{account.getName(),account.getServerType()});
        db.close();
    }

    public void insertOrUpdateAccount(Account account) {
        ContentValues cv = new ContentValues(5);
        cv.put("name",account.getName());
        cv.put("tokenKey",account.getAccessTokenKey());
        cv.put("tokenSecret",account.getAccessTokenSecret());
        cv.put("serverUrl",account.getServerUrl());
        cv.put("serverType",account.getServerType());

        SQLiteDatabase db = tdHelper.getWritableDatabase();
        if (getAccount(account.getName(),account.getServerType())==null)
            db.insert(TABLE_ACCOUNTS, null, cv);
        else
            db.update(TABLE_ACCOUNTS,cv,"name = ? AND serverType = ?",new String[]{account.getName(),account.getServerType()});
        db.close();

    }

    /**
     * Insert a direct message into the DB
     * @param id ID of the message
     * @param time creation time
     * @param json Json string of the message
     */
    public void insertDirect(int id, long time, String json) {
        ContentValues cv = new ContentValues(4);
        cv.put("id",id);
        cv.put("created_at", time);
        cv.put(ACCOUNT_ID,account);
        cv.put("message_json",json);

        SQLiteDatabase db = tdHelper.getWritableDatabase();
        db.insert(TABLE_DIRECTS,null,cv);
        db.close();
    }

    /**
     * Get a direct message from th DB
     * @param id ID of the message to look up
     * @return JSON string of the message or null if not found
     */
    public String getDirectById(int id) {

        SQLiteDatabase db = tdHelper.getReadableDatabase();
        String ret = null;

        Cursor c;
        c = db.query(TABLE_DIRECTS,new String[]{"message_json"},"id = ? AND " + ACCOUNT_ID_IS,new String[] { String.valueOf(id), account},null, null, null);
        if (c.getCount()>0) {
            c.moveToFirst();
            ret = c.getString(0);
        }
        return ret;
    }

    /**
     * Get the last <i>number</i> direct messages from the DB
     * @param number Numer of messages to get
     * @return List of messages or empyt list
     */
    public List<String> getDirects(int number) {
        SQLiteDatabase db = tdHelper.getReadableDatabase();
        List<String > ret = new ArrayList<String>();

        Cursor c;
        c = db.query(TABLE_DIRECTS,new String[]{"message_json"},ACCOUNT_ID_IS,new String[] { account},null, null, "ID DESC",String.valueOf(number));
        if (c.getCount()>0) {
            c.moveToFirst();
            do {
                String json = c.getString(0);
                ret.add(json);
            } while ((c.moveToNext()));
        }

        c.close();
        db.close();

        return ret;
    }


    public void storeSavedSearch(String name, String query, int id, String json) {
        ContentValues cv = new ContentValues(5);
        cv.put("id",id);
        cv.put("name", name);
        cv.put(ACCOUNT_ID,account);
        cv.put("query",query);
        cv.put("json",json);

        SQLiteDatabase db = tdHelper.getWritableDatabase();
        db.insert(TABLE_SEARCHES,null,cv);
        db.close();

    }

    public List<String> getSavedSearches() {
        SQLiteDatabase db = tdHelper.getReadableDatabase();
        List<String > ret = new ArrayList<String>();

        Cursor c;
        c = db.query(TABLE_SEARCHES,new String[]{"json"},ACCOUNT_ID_IS,new String[] { account},null, null, "ID DESC");
        if (c.getCount()>0) {
            c.moveToFirst();
            do {
                String json = c.getString(0);
                ret.add(json);
            } while ((c.moveToNext()));
        }
        c.close();
        db.close();

        return ret;
    }

    public void deleteSearch(int id) {
        SQLiteDatabase db = tdHelper.getWritableDatabase();
        db.delete(TABLE_SEARCHES,ACCOUNT_ID_IS + " AND id = ?",new String[]{account,String.valueOf(id)});
        db.close();
    }

}
