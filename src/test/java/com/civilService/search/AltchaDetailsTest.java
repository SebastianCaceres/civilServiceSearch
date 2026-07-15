package com.civilService.search;

import org.altcha.altcha.v1.Altcha;
import org.junit.jupiter.api.Test;

public class AltchaDetailsTest {

    @Test
    public void testDetails() throws Exception {
        System.out.println("=== ALTCHA DETAILS ===");
        String key = "my-test-hmac-key-signature-secret-which-is-long";
        var options = new Altcha.ChallengeOptions()
                .hmacKey(key)
                .algorithm(Altcha.Algorithm.SHA256)
                .maxNumber(100000)
                .expiresInSeconds(600);
        var challenge = Altcha.createChallenge(options);
        
        System.out.println("challenge.signature(): " + challenge.signature());
        System.out.println("challenge.algorithm(): " + challenge.algorithm());
        System.out.println("challenge.salt(): " + challenge.salt());
        System.out.println("challenge.challenge(): " + challenge.challenge());
        System.out.println("challenge.maxnumber(): " + challenge.maxnumber());
        System.out.println("======================");
    }
}
