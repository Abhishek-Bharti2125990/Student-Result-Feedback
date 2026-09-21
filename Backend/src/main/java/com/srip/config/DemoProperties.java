package com.srip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls first-start creation of one login per role, bound from {@code app.demo.*}.
 * Turn {@code seedUsers} off before running anywhere but a local machine.
 */
@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(boolean seedUsers, String defaultPassword) {

    public DemoProperties {
        defaultPassword = (defaultPassword == null || defaultPassword.isBlank())
                ? "Passw0rd!" : defaultPassword;
    }
}
