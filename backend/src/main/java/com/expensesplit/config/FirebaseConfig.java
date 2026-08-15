package com.expensesplit.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.config.path:classpath:firebase-service-account.json}")
    private String configPath;

    /**
     * Only registered when firebase.mock=false (or the property is absent).
     * In mock mode this bean does not exist at all, so nothing tries to inject it.
     */
    @Bean
    @ConditionalOnProperty(name = "firebase.mock", havingValue = "false", matchIfMissing = true)
    public FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream serviceAccount;
            if (configPath.startsWith("classpath:")) {
                String resourcePath = configPath.substring(10);
                serviceAccount = new ClassPathResource(resourcePath).getInputStream();
            } else {
                serviceAccount = new FileInputStream(configPath);
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            return FirebaseApp.initializeApp(options);
        }
        return FirebaseApp.getInstance();
    }

    /**
     * Only registered when firebase.mock=false (or the property is absent).
     * Depends on firebaseApp — both are absent together in mock mode.
     */
    @Bean
    @ConditionalOnProperty(name = "firebase.mock", havingValue = "false", matchIfMissing = true)
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}

