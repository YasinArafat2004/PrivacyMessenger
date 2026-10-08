package com.yasin.privacymessenger

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.yasin.privacymessenger.data.database.AppDatabase
import com.yasin.privacymessenger.data.database.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent

class ContactsActivity : AppCompatActivity() {

    private lateinit var btnBack: Button
    private lateinit var contactsContainer: LinearLayout
    private lateinit var tvEmptyContacts: TextView

    private val database by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_contacts)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->

            val systemBars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        btnBack = findViewById(R.id.btnBack)

        contactsContainer =
            findViewById(R.id.contactsContainer)

        tvEmptyContacts =
            findViewById(R.id.tvEmptyContacts)

        btnBack.setOnClickListener {
            finish()
        }

        loadContacts()
    }

    override fun onResume() {
        super.onResume()

        loadContacts()
    }

    private fun loadContacts() {

        lifecycleScope.launch(Dispatchers.IO) {

            val contacts =
                database.contactDao()
                    .getAllContacts()

            withContext(Dispatchers.Main) {

                displayContacts(contacts)
            }
        }
    }

    private fun displayContacts(
        contacts: List<Contact>
    ) {

        contactsContainer.removeAllViews()

        if (contacts.isEmpty()) {

            tvEmptyContacts.visibility =
                View.VISIBLE

            return
        }

        tvEmptyContacts.visibility =
            View.GONE

        for (contact in contacts) {

            addContactView(contact)
        }
    }

    private fun addContactView(
        contact: Contact
    ) {

        val card = LinearLayout(this)

        card.orientation =
            LinearLayout.HORIZONTAL

        card.gravity =
            Gravity.CENTER_VERTICAL

        card.setPadding(
            20,
            20,
            20,
            20
        )

        card.setBackgroundColor(
            Color.WHITE
        )

        val profile = TextView(this)

        profile.text =
            contact.displayName
                .first()
                .uppercase()

        profile.textSize = 20f

        profile.gravity =
            Gravity.CENTER

        profile.setTextColor(
            Color.WHITE
        )

        profile.setBackgroundColor(
            Color.rgb(70, 90, 120)
        )

        val profileParams =
            LinearLayout.LayoutParams(
                55,
                55
            )

        card.addView(
            profile,
            profileParams
        )

        val info = LinearLayout(this)

        info.orientation =
            LinearLayout.VERTICAL

        info.setPadding(
            16,
            0,
            0,
            0
        )

        val name = TextView(this)

        name.text =
            contact.displayName

        name.textSize = 18f

        name.setTextColor(
            Color.rgb(23, 32, 51)
        )

        name.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        val username = TextView(this)

        username.text =
            "@${contact.username}"

        username.textSize = 14f

        username.setTextColor(
            Color.rgb(104, 115, 134)
        )

        info.addView(name)

        info.addView(username)

        card.addView(
            info,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val chatButton = Button(this)

        chatButton.text =
            "Chat"

        chatButton.setOnClickListener {

            val intent = Intent(this, ChatActivity::class.java)

            intent.putExtra("username", contact.username)
            intent.putExtra("display_name", contact.displayName)

            startActivity(intent)
        }

        card.addView(chatButton)

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            0,
            0,
            16
        )

        contactsContainer.addView(
            card,
            params
        )
    }
}