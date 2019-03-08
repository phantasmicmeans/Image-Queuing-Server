package com.kt.narle.imageserver.storage;

import org.springframework.context.annotation.Configuration;

//@ConfigurationProperties("storage")
@Configuration
public class StorageProperties {

    /**
     * Folder location for storing files
     */
    private String location = "upload-dir";

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

}
