package ru.koregin.model;

public class Abonent {
    private int userId;
    private int port;
    private String switchIp;
    private String userName;

    public Abonent() {
    }

    public int getUserId() {
        return userId;
    }

    public int getPort() {
        return port;
    }

    public String getSwitchIp() {
        return switchIp;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setSwitchIp(String switchIp) {
        this.switchIp = switchIp;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
