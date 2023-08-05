package com.bitcoin_ring.virtualboltcard.cardSetup

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.bitcoin_ring.virtualboltcard.CardSetupManualActivity
import com.bitcoin_ring.virtualboltcard.R

class NoCardYetActivity : AppCompatActivity() {
    private lateinit var button_create: ImageButton
    private lateinit var button_import: ImageButton
    private lateinit var button_manual: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_card_yet)
        button_create = findViewById<View>(R.id.lnbitscreate) as ImageButton
        button_import = findViewById<View>(R.id.lnbitsimport) as ImageButton
        button_manual = findViewById<View>(R.id.manual) as ImageButton

        button_create.setOnClickListener {
            val intent = Intent(this, CardSetupLNbitsCreate::class.java)
            startActivity(intent)
        }
        button_import.setOnClickListener {
            val intent = Intent(this, CardSetupLNbitsImport::class.java)
            startActivity(intent)
        }
        button_manual.setOnClickListener {
            val intent = Intent(this, CardSetupManualActivity::class.java)
            intent.putExtra("action", R.id.cardsetup_create)
            intent.putExtra("card_id", -1)
            startActivity(intent)
        }
    }
}