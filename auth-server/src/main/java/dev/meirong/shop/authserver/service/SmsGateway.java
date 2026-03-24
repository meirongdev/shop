package dev.meirong.shop.authserver.service;

public interface SmsGateway {

    void send(String to, String message);
}
