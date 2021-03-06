package com.venefica.connect;

import com.venefica.auth.Token;
import com.venefica.auth.TokenEncryptor;
import com.venefica.dao.UserDao;
import com.venefica.model.User;
import java.util.Date;
import javax.inject.Inject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.web.SignInAdapter;
import org.springframework.social.support.URIBuilder;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Creates a session for interactions between mobile application and
 * web-services of the application and passes session id as a parameter.
 *
 * @author Sviatoslav Grebenchukov
 */
public class UserSignInAdapter implements SignInAdapter {

    private static final String PARAM_TOKEN = "token";
    private static final String PROFILE_URL = "/profile";
    
    private final Log log = LogFactory.getLog(UserSignInAdapter.class);
    
    @Inject
    private TokenEncryptor tokenEncryptor;
    
    @Inject
    private UserDao userDao;

    @Override
    public String signIn(String userId, Connection connection, NativeWebRequest request) {
        try {
            Token token = new Token(Long.parseLong(userId));
            String encryptedToken = tokenEncryptor.encrypt(token);
            
            User user = userDao.get(token.getUserId());
            user.setPreviousLoginAt(user.getLastLoginAt());
            user.setLastLoginAt(new Date());
            userDao.update(user);
            
            return URIBuilder.fromUri(PROFILE_URL).queryParam(PARAM_TOKEN, encryptedToken).build().toString();
        } catch (Exception e) {
            log.error("Exception thrown", e);
            throw new RuntimeException(e);
        }
    }
}
