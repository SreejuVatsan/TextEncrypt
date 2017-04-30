package de.ub0r.android.smsdroid;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class GetEncrypPopup extends Activity {
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.get_encrypt_passkey2);
		final EditText passkey = (EditText) this.findViewById(R.id.password2);
		final Dialog dialog = new Dialog(GetEncrypPopup.this);
		dialog.setTitle("Enter the Pass Key");
		dialog.setContentView(R.layout.get_encrypt_passkey);

		Button ok = (Button) this.findViewById(R.id.dialogOk2);
		Button cancel = (Button) this.findViewById(R.id.dialogCancel2);
		ok.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(final View v) {
				if (passkey.getText().toString().equals("")) {
					Toast.makeText(GetEncrypPopup.this.getApplicationContext(),
							"Sorry..!! Blank Password Not Allowed :)", Toast.LENGTH_SHORT).show();

				} else {
					SenderActivity.PASS_KEY = passkey.getText().toString();
					dialog.dismiss();
					GetEncrypPopup.this.finish();
				}

			}
		});
		cancel.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(final View v) {
				dialog.dismiss();
				GetEncrypPopup.this.finish();

			}
		});
	}
}
