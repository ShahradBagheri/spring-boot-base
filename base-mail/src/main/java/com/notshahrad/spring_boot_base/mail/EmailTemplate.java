package com.notshahrad.spring_boot_base.mail;

@FunctionalInterface
public interface EmailTemplate<T> {

    EmailMessage render(T data);
}
