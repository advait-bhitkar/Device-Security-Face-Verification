package com.silverstudio.devicesecurity.fragment

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.silverstudio.devicesecurity.R

/**
 * This the home fragment
 * It is the main fragment where user can add or
 * edit the note
 */
class HomeFragment : Fragment() {

    /*
    Declare variables
     */
    private lateinit var textMessage: TextInputEditText
    private lateinit var buttonSaveMessage: MaterialButton
    private lateinit var buttonAddFaceSecurity: MaterialButton
    private lateinit var toolbar: MaterialToolbar
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {

        auth = FirebaseAuth.getInstance()
        val sharedPref = requireActivity().getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val isFaceSecurityAdded = sharedPref.getBoolean("isFaceSecurityAdded", false)
        val isFaceVerified = sharedPref.getBoolean("isFaceVerified", false)

        /**
         * Check is user is logged in
         * If not logged in redirect the use to LoginFragment
         */
        if (auth.currentUser == null)
        {
            findNavController().navigate(R.id.action_homeFragment_to_loginFragment)

        }
        /**
         * Check if face security feature is enabled
         * If enabled check is face is identified for the session
         * by checking 'isFaceVerified' variable
         * If face is not verified redirect user to VerifyFaceFragment
         */
        else if (isFaceSecurityAdded && !isFaceVerified)
        {
            findNavController().navigate(R.id.action_homeFragment_to_verifyFaceFragment)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /**
         * Initialize the views
         */
        toolbar = view.findViewById(R.id.toolbar)
        textMessage = view.findViewById(R.id.message_text)
        buttonSaveMessage = view.findViewById(R.id.save_message_button)
        buttonAddFaceSecurity = view.findViewById(R.id.face_security_button)

        /**
         * Set logout feature to toolbar options menu
         */
        toolbar.setOnMenuItemClickListener {
            when(it.itemId) {
                R.id.logout -> {
                    Firebase.auth.signOut()
                    findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
                }
            }
            true
        }

        val sharedPref = requireActivity().getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        if (sharedPref.getString("message", "").toString().isNotEmpty())
            textMessage.setText(sharedPref.getString("message", ""))

        /**
         * Set the text of button accordingly,
         */
        if (sharedPref.getBoolean("isFaceSecurityAdded", false))
            buttonAddFaceSecurity.text = "Remove Face Security"
        else
            buttonAddFaceSecurity.text = "Add Face security"


        /**
         * Save the secret note to the shared preference
         * toobar.hasFocus() is used to remove the focus from the editText
         */
        buttonSaveMessage.setOnClickListener {
            sharedPref.edit().putString("message",textMessage.text.toString()).apply()
            toolbar.hasFocus()
            /**
             * Collapse keyboard
             */
            try {
                val imm: InputMethodManager? =
                    requireActivity().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
                imm!!.hideSoftInputFromWindow(requireActivity().currentFocus!!.windowToken, 0)
            } catch (e: Exception) {
                Log.d("error", e.toString())
            }

            Toast.makeText(requireActivity(),"Secret note saved successfully", Toast.LENGTH_SHORT).show()
        }

        buttonAddFaceSecurity.setOnClickListener {

            if (sharedPref.getBoolean("isFaceSecurityAdded", false))
            {
                buttonAddFaceSecurity.text = "Add Face security"
                sharedPref.edit().putBoolean("isFaceSecurityAdded",false).apply()
            }else
            {
                findNavController().navigate(R.id.action_homeFragment_to_addFaceSecurityFragment)
                buttonAddFaceSecurity.text = "Remove Face Security "
            }
        }
    }


}