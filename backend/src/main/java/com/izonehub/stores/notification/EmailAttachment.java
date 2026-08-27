package com.izonehub.stores.notification;

public record EmailAttachment(String filename, byte[] content, String contentType) {}
