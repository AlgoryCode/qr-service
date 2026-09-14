package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.dto.GoogleOidcIdentity;

public interface GoogleIdTokenVerifier {

    GoogleOidcIdentity verify(String idToken);
}
