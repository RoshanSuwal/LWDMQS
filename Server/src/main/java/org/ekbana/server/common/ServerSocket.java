package org.ekbana.server.common;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ServerSocket<T> {

//    private  final InetSocketAddress inetSocketAddress;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;

    private final AtomicBoolean alive;

    protected ServerSocket( ) {
        this.alive = new AtomicBoolean(true);
    }

//    public ServerSocket(String address,int port){
////        this.inetSocketAddress=new InetSocketAddress(address,port);
//        this.alive=new AtomicBoolean(true);
//    }

    public void startServer(int[] ports) throws IOException {
        this.selector=Selector.open();
//        serverSocketChannel=ServerSocketChannel.open();
//        serverSocketChannel.configureBlocking(false);
//        serverSocketChannel.socket().bind(new InetSocketAddress());
//        serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);

        for (int port:ports){
            ServerSocketChannel serverSocketChannel=ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().bind(new InetSocketAddress(port));
            serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
            ((KafkaServer.KafkaServerListener)getPortListener(port)).onStart();
//            onStart(port);
        }
        startServing();
    }

    private void startServing() throws IOException {
        while (alive.get()){
            this.selector.select();
            final Iterator<SelectionKey> selectionKeys = this.selector.selectedKeys().iterator();
            while (selectionKeys.hasNext()){
                SelectionKey selectionKey=selectionKeys.next();
                selectionKeys.remove();

                if (!selectionKey.isValid()) continue;
                if (selectionKey.isAcceptable()) accept(selectionKey);
                else if (selectionKey.isReadable()) read(selectionKey);
            }
        }
        closeServer();
    }

    private void accept(SelectionKey selectionKey) throws IOException {
        final ServerSocketChannel serverSocketChannel = (ServerSocketChannel)selectionKey.channel();
        SocketChannel socketChannel=serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        final Socket socket = socketChannel.socket();

        final SocketAddress remoteSocketAddress = socket.getRemoteSocketAddress();
        System.out.println("Connected to : "+remoteSocketAddress);
        // register
        System.out.println(socket.getPort()+":"+socket.getLocalPort());
        final KafkaServer.KafkaServerListener portListener = (KafkaServer.KafkaServerListener) getPortListener(socket.getLocalPort());
        final KafkaServer.KafkaServerClient attachment = portListener.createAttachment(socketChannel);
//        final T attachment = createAttachment(socketChannel);
        portListener.onConnectionCreated(attachment);
//        onConnectionCreated(attachment);
        socketChannel.register(this.selector,SelectionKey.OP_READ, attachment);
        // start starting request
    }

    private void read(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel= (SocketChannel) selectionKey.channel();
        ByteBuffer byteBuffer=ByteBuffer.allocate(1024*64);
        int numRead=-1;
        numRead=socketChannel.read(byteBuffer);
        byteBuffer.flip();

        final KafkaServer.KafkaServerListener portListener = (KafkaServer.KafkaServerListener)getPortListener(socketChannel.socket().getLocalPort());
        if (numRead==-1){
            portListener.onConnectionClose((KafkaServer.KafkaServerClient) selectionKey.attachment());
//            onConnectionClose((T) selectionKey.attachment());
            closeConnection(selectionKey);
        }else {
            // send read
            byte[] readByte=new byte[numRead];
            System.arraycopy(byteBuffer.array(),0,readByte,0,numRead);
//            onRead((T) selectionKey.attachment(),readByte);
            portListener.onRead((KafkaServer.KafkaServerClient)selectionKey.attachment(),readByte);
        }
        byteBuffer.clear();
    }

    private void closeConnection(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel= (SocketChannel) selectionKey.channel();
        Socket socket= socketChannel.socket();
        SocketAddress socketAddress=socket.getRemoteSocketAddress();
        System.out.println("Connection closed by client : "+socketAddress);

        //send closing connection callback

        socketChannel.close();
        socket.close();
        selectionKey.cancel();
    }

    public void stop(){
        this.alive.set(false);
    }

    private void closeServer() throws IOException {

        if (this.selector!=null)this.selector.close();
        if (this.serverSocketChannel!=null)this.serverSocketChannel.close();
        System.out.println("Server closed");
    }

//    protected abstract T createAttachment(SocketChannel socketChannel) throws IOException;
//
//    protected abstract void onConnectionCreated(T t);
//    protected abstract void onConnectionClose(T t);
//    protected abstract void onRead(T t,byte[] bytes);
//    protected abstract void onStart(int port);
    protected abstract T getPortListener(int port);

}
