package com.ael.algoryqrservice.authservice.application.auth;

import com.ael.algoryqrservice.authservice.util.ClientInfo;

public interface GoogleSubjectStrategy {

    String audience();

    AuthSubject login(GoogleOidcIdentity identity);

    AuthSubject register(GoogleOidcIdentity identity, ClientInfo clientInfo, String provider);
}
