package pitiupi.net.tcp;

import pitiupi.models.Messages.Message;

class HandshakeMessage extends Message {
    private final boolean isPrivate;
    private final String sender;
    private final String receiver;


    public HandshakeMessage(boolean isPrivate, String sender, String receiver) {
        this.isPrivate = isPrivate;
        this.sender = sender;
        this.receiver = receiver;
    }

    public boolean isPrivate() {
        return isPrivate;
    }
    public String getSender() {
        return sender;
    }
    public String getReceiver() {
        return receiver;
    }

    public HandshakeMessage createResponse() {
        return new HandshakeMessage(isPrivate, receiver, sender);
    }
}
