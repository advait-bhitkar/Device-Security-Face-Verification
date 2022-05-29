package com.silverstudio.devicesecurity.fragment

import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.silverstudio.devicesecurity.R

/**
 * Login fragment is used to login the user
 * This apps implements Login with google for login
 */
class LoginFragment : Fragment() {

    /**
     * Declare variables and views
     */
    private val RC_SIGN_IN = 700
    private lateinit var buttonSignIn: MaterialButton
    private lateinit var mSignInClient: GoogleSignInClient
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var dialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttonSignIn = view.findViewById(R.id.sign_in_button)
        /**
         * Initialize google sign in flow
         * we need to paas tokenId from firebase
         * which is already in gooogle-services.json
         */
        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        mSignInClient = GoogleSignIn.getClient(requireActivity(), googleSignInOptions)
        mFirebaseAuth = FirebaseAuth.getInstance()
        buttonSignIn.setOnClickListener {
            signIn()
        }

    }

    /**
     * Sign in function
     * Show the loading progress dialog
     */
    private fun signIn() {
        dialog = ProgressDialog.show(requireActivity(), "", "Please wait...", true);
        val signInIntent = mSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    /**
     * Result returned form the signIn intent
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        dialog.dismiss()
        /**
         * If success
         * call firebaseAuthWithGoogle()
         */
        if (requestCode == RC_SIGN_IN) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                Log.w(ContentValues.TAG, "Google sign in failed", e)
            }
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        /**
         * Get credentials and sign in to firebase
         */
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        mFirebaseAuth.signInWithCredential(credential)
            .addOnSuccessListener(requireActivity()) {
                Toast.makeText(
                    requireActivity(), "Authentication Success.",
                    Toast.LENGTH_SHORT
                ).show()
                findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
            }
            .addOnFailureListener(requireActivity()) { e ->
                Toast.makeText(
                    requireActivity(), "Authentication failed.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

}