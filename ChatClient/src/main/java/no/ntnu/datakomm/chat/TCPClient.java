package no.ntnu.datakomm.chat;

import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class TCPClient {
    private PrintWriter toServer;
    private BufferedReader fromServer;
    private Socket connection;

    // Hint: if you want to store a message for the last error, store it here
    private String lastError = null;

    private final List<ChatListener> listeners = new LinkedList<>();

    /**
     * Connect to a chat server.
     *
     * @param host host name or IP address of the chat server
     * @param port TCP port of the chat server
     * @return True on success, false otherwise
     */
    public boolean connect(String host, int port) {

        boolean connected = false;

        InetSocketAddress serverAddress = new InetSocketAddress(host, port);


        try
        {
            connection = new Socket();
            connection.connect(serverAddress);
            toServer = new PrintWriter(connection.getOutputStream(), true);
            InputStream in = connection.getInputStream();
            fromServer = new BufferedReader(new InputStreamReader(in));

            connected = true;

        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        return connected;
    }


    /**
     * Close the socket. This method must be synchronized, because several
     * threads may try to call it. For example: When "Disconnect" button is
     * pressed in the GUI thread, the connection will get closed. Meanwhile, the
     * background thread trying to read server's response will get error in the
     * input stream and may try to call this method when the socket is already
     * in the process of being closed. with "synchronized" keyword we make sure
     * that no two threads call this method in parallel.
     */
    public synchronized void disconnect() {
        if (isConnectionActive()) {
            try {
                toServer = null;
                fromServer = null;
                connection.close();
                connection = null;
                onDisconnect();
            } catch (IOException i){
                System.out.print("A socket error occurred");
            }
        }
    }


    /**
     * @return true if the connection is active (opened), false if not.
     */
    public boolean isConnectionActive() {
        return connection != null;
    }

    /**
     * Send a command to server.
     *
     * @param cmd A command. It should include the command word and optional attributes, according to the protocol.
     */
    private void sendCommand(String cmd) {
        if(isConnectionActive()){
            try{
                toServer.println(cmd);
            } catch (Exception e) {
                System.out.print("A socket error occurred");
            }
        } else {
            System.out.println("The connection was closed");
        }
    }


    /**
     * Send a public message to all the recipients.
     *
     * @param message Message to send
     */
    public void sendPublicMessage(String message) {
        try {
            if(message.equals("/joke")){
                sendCommand("joke");
            } else {
                sendCommand("msg " + message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Send a login request to the chat server.
     *
     * @param username Username to use
     */
    public void tryLogin(String username) {
        try {
            sendCommand("login " + username);
            refreshUserList();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Send a request for latest user list to the server. To get the new users,
     * clear your current user list and use events in the listener.
     */
    public void refreshUserList() {
        sendCommand("users");
    }

    /**
     * Send a private message to a single recipient.
     *
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     */
    public void sendPrivateMessage(String recipient, String message) {
        if (isConnectionActive()) {
            try {
                sendCommand("privmsg " + recipient + " " + message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


    /**
     * Send a request for the list of commands that server supports.
     */
    public void askSupportedCommands() {
        try {
            sendCommand("help");

        }catch (Exception e){
            e.printStackTrace();

        }
    }



    /**
     * Wait for chat server's response
     *
     * @return one line of text (one command) received from the server
     */
    private String waitServerResponse() {
        String response = null;
        try {
            response = fromServer.readLine();
            if (response == null) {
                disconnect();
            }

        } catch (Exception e){
            e.printStackTrace();
            disconnect();
        }

        return response;
    }


    /**
     * Get the last error message
     *
     * @return Error message or "" if there has been no error
     */
    public String getLastError() {
        return Objects.requireNonNullElse(lastError, "");
    }

    /**
     * Start listening for incoming commands from the server in a new CPU thread.
     */
    public void startListenThread() {
        // Call parseIncomingCommands() in the new thread.
        Thread t = new Thread(this::parseIncomingCommands);
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands() {
        try {

            while (isConnectionActive()) {

                String serverResponse = waitServerResponse(); //Lagrer server responsen som en String
                String inputCase = null;
                String serverMessage = "";

                if (serverResponse != null) {
                    String[] responseArr = serverResponse.split(" ", 2); //Legger til responen i en array, og splitter første ordet some er koden i inputcase
                    inputCase = responseArr[0]; //Bytter til riktig case iforhold til serverresponsen
                    if (responseArr.length > 1) {
                        serverMessage = responseArr[1];
                    }


                }

                switch (Objects.requireNonNull(inputCase)) {
                    case "loginok\n":
                        onLoginResult(true, "");
                        break;

                    case "loginerr":
                        onLoginResult(false, serverMessage);
                        break;

                    case "msgerr":
                        onMsgError(serverMessage);
                        break;

                    case "supported":
                        String[] supportedCommands = serverMessage.split(" ");
                        onSupported(supportedCommands);
                        break;

                    case "cmderr":
                        onCmdError(serverMessage);
                        break;

                    case "users":
                        String[] usersArr = serverMessage.split(" ");
                        onUsersList(usersArr);
                        break;

                    case "msg":
                        if (serverMessage != null){
                            String[] messageArr = serverMessage.split(" ",2);
                            String sender = messageArr[0];
                            String publicMessage = messageArr[1];
                            onMsgReceived(false, sender , publicMessage);
                        }
                        break;

                    case "privmsg":
                        if (serverMessage != null){
                            String[] messageArr = serverMessage.split(" ",2);
                            String sender = messageArr[0];
                            String privateMessage = messageArr[1];
                            onMsgReceived(true, sender , privateMessage);
                        }
                        break;

                    default:
                        break;
                }


            }
        }catch (NullPointerException e)
        {
            System.out.println(e);
        }

    }

    /**
     * Register a new listener for events (login result, incoming message, etc)
     *
     * @param listener listener
     */
    public void addListener(ChatListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     *
     * @param listener listener
     */
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The following methods are all event-notificators - notify all the listeners about a specific event.
    // By "event" here we mean "information received from the chat server".
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Notify listeners that login operation is complete (either with success or
     * failure)
     *
     * @param success When true, login successful. When false, it failed
     * @param errMsg  Error message if any
     */
    private void onLoginResult(boolean success, String errMsg) {
        for (ChatListener l : listeners) {
            l.onLoginResult(success, errMsg);
        }
    }

    /**
     * Notify listeners that socket was closed by the remote end (server or
     * Internet error)
     */
    private void onDisconnect() {
        for (ChatListener l : listeners) {
            l.onDisconnect();
        }
    }

    /**
     * Notify listeners that server sent us a list of currently connected users
     *
     * @param users List with usernames
     */
    private void onUsersList(String[] users) {
        for (ChatListener l : listeners)
            l.onUserList(users);
    }

    /**
     * Notify listeners that a message is received from the server
     *
     * @param priv   When true, this is a private message
     * @param sender Username of the sender
     * @param text   Message text
     */
    private void onMsgReceived(boolean priv, String sender, String text) {
        for (ChatListener l : listeners){
            l.onMessageReceived(new TextMessage(sender,priv,text));
        }
    }

    /**
     * Notify listeners that our message was not delivered
     *
     * @param errMsg Error description returned by the server
     */
    private void onMsgError(String errMsg) {
        for (ChatListener l :listeners){
            l.onMessageError(errMsg);
        }
    }

    /**
     * Notify listeners that command was not understood by the server.
     *
     * @param errMsg Error message
     */
    private void onCmdError(String errMsg) {
        for (ChatListener l : listeners) {
            l.onCommandError(errMsg);

        }
    }

    /**
     * Notify listeners that a help response (supported commands) was received
     * from the server
     *
     * @param commands Commands supported by the server
     */
    private void onSupported(String[] commands) {
        for (ChatListener l : listeners){
            l.onSupportedCommands(commands);
        }
    }
}
