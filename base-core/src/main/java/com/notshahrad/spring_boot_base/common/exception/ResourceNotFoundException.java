package com.notshahrad.spring_boot_base.common.exception;

public class ResourceNotFoundException extends BaseException{

    public ResourceNotFoundException(String resourceName, Object resourceId) {
        super(resourceName + " not found with id: " + resourceId);
    }
}
