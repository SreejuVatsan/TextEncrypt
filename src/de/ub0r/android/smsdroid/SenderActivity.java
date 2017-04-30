/**
 * 
 */
package de.ub0r.android.smsdroid;

import java.net.URLDecoder;
import java.util.ArrayList;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import de.ub0r.android.lib.Log;
import de.ub0r.android.lib.apis.ContactsWrapper;

/**
 * Class sending messages via standard Messaging interface.
 * 
 * @author flx
 */
public final class SenderActivity extends SherlockActivity implements OnClickListener {
	/** Tag for output. */
	private static final String TAG = "send";

	/** {@link Uri} for saving messages. */
	private static final Uri URI_SMS = Uri.parse("content://sms");
	/** {@link Uri} for saving sent messages. */
	public static final Uri URI_SENT = Uri.parse("content://sms/sent");
	/** Projection for getting the id. */
	private static final String[] PROJECTION_ID = new String[] { BaseColumns._ID };
	/** SMS DB: address. */
	private static final String ADDRESS = "address";
	/** SMS DB: read. */
	private static final String READ = "read";
	/** SMS DB: type. */
	public static final String TYPE = "type";
	/** SMS DB: body. */
	private static final String BODY = "body";
	/** SMS DB: date. */
	private static final String DATE = "date";

	/** Message set action. */
	public static final String MESSAGE_SENT_ACTION = "com.android.mms.transaction.MESSAGE_SENT";
	// ----------------------------------------------------------------------------------------------------------------------------------------------
	private static int ENCR = 0;
	private static boolean PASS_SET = false;
	public static String PASS_KEY;

	// ----------------------------------------------------------------------------------------------------------------------------------------------

	/** Hold recipient and text. */
	private String to, text;
	/** {@link ClipboardManager}. */
	@SuppressWarnings("deprecation")
	private ClipboardManager cbmgr;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.handleIntent(this.getIntent());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onNewIntent(final Intent intent) {
		super.onNewIntent(intent);
		this.handleIntent(intent);
	}

	/**
	 * Handle {@link Intent}.
	 * 
	 * @param intent
	 *            {@link Intent}
	 */
	@SuppressWarnings("deprecation")
	private void handleIntent(final Intent intent) {
		if (this.parseIntent(intent)) {
			this.setTheme(android.R.style.Theme_Translucent_NoTitleBar);
			this.send();
			this.finish();
		} else {
			this.setTheme(PreferencesActivity.getTheme(this));
			SMSdroid.fixActionBarBackground(this.getSupportActionBar(), this.getResources(),
					R.drawable.bg_striped, R.drawable.bg_striped_img);
			this.setContentView(R.layout.sender);

			// ------------------------------------------------------------------------

			View customNav = LayoutInflater.from(this).inflate(R.layout.encrypt_check, null);

			CheckBox cb = (CheckBox) customNav.findViewById(R.id.checkBox1);
			cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {

				@Override
				public void onCheckedChanged(final CompoundButton buttonView,
						final boolean isChecked) {
					if (isChecked) {
						ENCR = 1;
						if (!PASS_SET) {
							Intent i = new Intent(SenderActivity.this.getApplicationContext(),
									GetEncrypPopup.class);

							SenderActivity.this.startActivity(i);
							PASS_SET = true;
						} else {
							Toast.makeText(
									SenderActivity.this,
									"CheckBox Clicked!! " + ENCR + " " + PASS_SET + " Password is "
											+ PASS_KEY, Toast.LENGTH_SHORT).show();
						}

					} else {
						ENCR = 0;
						Toast.makeText(SenderActivity.this,
								"CheckBox Clicked!! " + ENCR + " " + PASS_SET, Toast.LENGTH_SHORT)
								.show();
					}

				}
			});

			this.getSupportActionBar().setCustomView(customNav);
			this.getSupportActionBar().setDisplayShowCustomEnabled(true);

			// ------------------------------------------------------------------------
			this.findViewById(R.id.text_paste).setOnClickListener(this);
			final EditText et = (EditText) this.findViewById(R.id.text);
			et.addTextChangedListener(new MyTextWatcher(this, (TextView) this
					.findViewById(R.id.text_paste), (TextView) this.findViewById(R.id.text_)));
			et.setText(this.text);
			final MultiAutoCompleteTextView mtv = (MultiAutoCompleteTextView) this
					.findViewById(R.id.to);
			mtv.setAdapter(new MobilePhoneAdapter(this));
			mtv.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
			mtv.setText(this.to);
			if (!TextUtils.isEmpty(this.to)) {
				this.to = this.to.trim();
				if (this.to.endsWith(",")) {
					this.to = this.to.substring(0, this.to.length() - 1).trim();
				}
				if (this.to.indexOf('<') < 0) {
					// try to fetch recipient's name from phone book
					String n = ContactsWrapper.getInstance().getNameForNumber(
							this.getContentResolver(), this.to);
					if (n != null) {
						this.to = n + " <" + this.to + ">, ";
					}
				}
				mtv.setText(this.to);
				et.requestFocus();
			} else {
				mtv.requestFocus();
			}
			this.cbmgr = (ClipboardManager) this.getSystemService(CLIPBOARD_SERVICE);
		}
	}

	/**
	 * Parse data pushed by {@link Intent}.
	 * 
	 * @param intent
	 *            {@link Intent}
	 * @return true if message is ready to send
	 */
	private boolean parseIntent(final Intent intent) {
		Log.d(TAG, "parseIntent(" + intent + ")");
		if (intent == null) {
			return false;
		}
		Log.d(TAG, "got action: " + intent.getAction());

		this.to = null;
		String u = intent.getDataString();
		try {
			if (!TextUtils.isEmpty(u) && u.contains(":")) {
				String t = u.split(":")[1];
				if (t.startsWith("+")) {
					this.to = "+" + URLDecoder.decode(t.substring(1));
				} else {
					this.to = URLDecoder.decode(t);
				}
			}
		} catch (IndexOutOfBoundsException e) {
			Log.w(TAG, "could not split at :", e);
		}
		u = null;

		CharSequence cstext = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
		this.text = null;
		if (cstext != null) {
			this.text = cstext.toString();
			cstext = null;
		}
		if (TextUtils.isEmpty(this.text)) {
			Log.i(TAG, "text missing");
			return false;
		}
		if (TextUtils.isEmpty(this.to)) {
			Log.i(TAG, "recipient missing");
			return false;
		}

		return true;
	}

	/**
	 * Send a message to a single recipient.
	 * 
	 * @param recipient
	 *            recipient
	 * @param message
	 *            message
	 */
	private void send(final String recipient, final String message) {
		Log.d(TAG, "text: " + recipient);
		int[] l = SmsMessage.calculateLength(message, false);
		Log.i(TAG, "text7: " + message.length() + ", " + l[0] + " " + l[1] + " " + l[2] + " "
				+ l[3]);
		l = SmsMessage.calculateLength(message, true);
		Log.i(TAG, "text8: " + message.length() + ", " + l[0] + " " + l[1] + " " + l[2] + " "
				+ l[3]);

		// save draft
		final ContentResolver cr = this.getContentResolver();
		ContentValues values = new ContentValues();
		values.put(TYPE, Message.SMS_DRAFT);
		values.put(BODY, message);
		values.put(READ, 1);
		values.put(ADDRESS, recipient);
		Uri draft = null;
		// save sms to content://sms/sent
		Cursor cursor = cr.query(URI_SMS, PROJECTION_ID,
				TYPE + " = " + Message.SMS_DRAFT + " AND " + ADDRESS + " = '" + recipient
						+ "' AND " + BODY + " like '" + message.replace("'", "_") + "'", null, DATE
						+ " DESC");
		if (cursor != null && cursor.moveToFirst()) {
			draft = URI_SENT.buildUpon().appendPath(cursor.getString(0)).build();
			Log.d(TAG, "skip saving draft: " + draft);
		} else {
			try {
				draft = cr.insert(URI_SENT, values);
				Log.d(TAG, "draft saved: " + draft);
			} catch (SQLiteException e) {
				Log.e(TAG, "unable to save draft", e);
			}
		}
		values = null;
		if (cursor != null && !cursor.isClosed()) {
			cursor.close();
		}
		cursor = null;
		SmsManager smsmgr = SmsManager.getDefault();
		final ArrayList<String> messages = smsmgr.divideMessage(message);
		final int c = messages.size();
		ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>(c);

		try {
			Log.d(TAG, "send messages to: " + recipient);

			for (int i = 0; i < c; i++) {
				final String m = messages.get(i);
				Log.d(TAG, "devided messages: " + m);

				final Intent sent = new Intent(MESSAGE_SENT_ACTION, draft, this, SmsReceiver.class);
				sentIntents.add(PendingIntent.getBroadcast(this, 0, sent, 0));
			}
			smsmgr.sendMultipartTextMessage(recipient, null, messages, sentIntents, null);
			Log.i(TAG, "message sent");
		} catch (Exception e) {
			Log.e(TAG, "unexpected error", e);
			for (PendingIntent pi : sentIntents) {
				if (pi != null) {
					try {
						pi.send();
					} catch (CanceledException e1) {
						Log.e(TAG, "unexpected error", e1);
					}
				}
			}
		}
	}

	/**
	 * Send a message.
	 * 
	 * @return true, if message was sent
	 */
	private boolean send() {
		if (TextUtils.isEmpty(this.to) || TextUtils.isEmpty(this.text)) {
			return false;
		}

		// -------------------------------------------------------------------
		if (ENCR == 0) {

			for (String r : this.to.split(",")) {
				r = MobilePhoneAdapter.cleanRecipient(r);
				if (TextUtils.isEmpty(r)) {
					Log.w(TAG, "skip empty recipipient: " + r);
					continue;
				}
				this.send(r, this.text);
			}

		}

		else {
			try {
				this.text = EncrDecr.encrypt(SenderActivity.PASS_KEY, this.text);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			for (String r : this.to.split(",")) {
				r = MobilePhoneAdapter.cleanRecipient(r);
				if (TextUtils.isEmpty(r)) {
					Log.w(TAG, "skip empty recipipient: " + r);
					continue;
				}
				this.send(r, this.text);
			}
		}

		// -------------------------------------------------------------------

		// for (String r : this.to.split(",")) {
		// r = MobilePhoneAdapter.cleanRecipient(r);
		// if (TextUtils.isEmpty(r)) {
		// Log.w(TAG, "skip empty recipipient: " + r);
		// continue;
		// }
		// this.send(r, this.text);
		// }
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void onClick(final View v) {
		switch (v.getId()) {
		case R.id.text_paste:
			final CharSequence s = this.cbmgr.getText();
			((EditText) this.findViewById(R.id.text)).setText(s);
			return;
		default:
			break;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		this.getSupportMenuInflater().inflate(R.menu.sender, menu);
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {

		switch (item.getItemId()) {
		case android.R.id.home:
			// app icon in Action Bar clicked; go home
			Intent intent = new Intent(this, ConversationListActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			this.startActivity(intent);
			return true;
		case R.id.item_send:
			EditText et = (EditText) this.findViewById(R.id.text);
			this.text = et.getText().toString();
			et = (MultiAutoCompleteTextView) this.findViewById(R.id.to);
			this.to = et.getText().toString();
			if (this.send()) {
				this.finish();
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	// void showDialoguebox() {
	// final EditText passkey = (EditText) this.findViewById(R.id.password2);
	// Button ok = (Button) this.findViewById(R.id.dialogOk2);
	// Button cancel = (Button) this.findViewById(R.id.dialogCancel2);
	// final Dialog dialog = new Dialog(SenderActivity.this);
	// dialog.setContentView(R.layout.get_encrypt_passkey);
	// dialog.setTitle("Enter the Pass Key");
	// // Button ok = (Button) this.findViewById(R.id.dialogOk);
	// // Button cancel = (Button) this.findViewById(R.id.dialogCancel);
	//
	// if (!PASS_SET) {
	//
	// dialog.show();
	//
	// }
	//
	// ok.setOnClickListener(new OnClickListener() {
	//
	// @Override
	// public void onClick(final View v) {
	// SenderActivity.PASS_KEY = passkey.getText().toString();
	// dialog.dismiss();
	// Toast.makeText(SenderActivity.this, "CheckBox Clicked!! " + ENCR + " " +
	// PASS_SET,
	// Toast.LENGTH_SHORT).show();
	// }
	// });
	//
	// cancel.setOnClickListener(new OnClickListener() {
	//
	// @Override
	// public void onClick(final View v) {
	// dialog.dismiss();
	// Toast.makeText(SenderActivity.this, "CheckBox Clicked!! " + ENCR + " " +
	// PASS_SET,
	// Toast.LENGTH_SHORT).show();
	//
	// }
	// });
	//
	// }

}
