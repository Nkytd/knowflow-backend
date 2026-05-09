package com.knowflow.parser.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class ParseWorkerIdentityProvider {

    private final String workerId;

    public ParseWorkerIdentityProvider(@Value("${spring.application.name:knowflow-backend}") String applicationName) {
        this.workerId = "%s@%s#%d".formatted(
                applicationName,
                resolveHostName(),
                ProcessHandle.current().pid()
        );
    }

    public String getWorkerId() {
        return workerId;
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "unknown-host";
        }
    }
}
