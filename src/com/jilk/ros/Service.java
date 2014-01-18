/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.jilk.ros;

import com.jilk.ros.message.Message;
import com.jilk.ros.rosbridge.MessageHandler;
import com.jilk.ros.rosbridge.ROSBridgeClient;
import com.jilk.ros.rosbridge.operation.CallService;
import com.jilk.ros.rosbridge.operation.ServiceResponse;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

/*
 * TODO: x 1. create an inner class instead of having three separate hashes.
 *       x 2. write the basic reflection calls for services, topics, and data types
 *       3. build the "implements" method for TypeDef (compare with Java class)
 *       4. write the verify methods for Service and Topic
 */


/**
 *
 * @author David J. Jilk
 */
public class Service<CallType extends Message, ResponseType extends Message> implements MessageHandler {
    private String service;
    private Class<ResponseType> responseType;
    private Class<CallType> callType;
    private ROSBridgeClient client;
    private Map<String, CallRecord> calls;
    
    public Service(String service, Class<CallType> callType, Class<ResponseType> responseType, ROSBridgeClient client) {
        this.service = service;
        this.client = client;
        this.responseType = responseType;
        this.callType = callType;
        calls = new HashMap<String, CallRecord>();
    }

    // A result can only be returned once; it is cleared from the hash before 
    //   being sent/returned. This is to ensure that results do not accumulate
    //   indefinitely.  If callers need to keep these around they can set up their 
    //   own hash.
    
    @Override
    public void onMessage(String id, Message response) {        
        //System.out.print("Service.onMessage: ");
        //response.print();
        CallRecord call = calls.get(id);
        if (call.handler != null) {
            calls.remove(id);
            call.handler.onMessage(id, (ResponseType) response);
        }
        else {
            call.result = (ResponseType) response;
            call.latch.countDown();
        }
    }
    
    public String call(CallType args) {
        return callImpl(args, null);
    }
    
    public void callWithHandler(CallType args, MessageHandler<ResponseType> responseHandler) {
        callImpl(args, responseHandler);
    }
    
    public ResponseType callBlocking(CallType args) throws InterruptedException {
        return take(call(args));
    }
    
    private String callImpl(CallType args, MessageHandler<ResponseType> responseHandler) {
        client.register(ServiceResponse.class, service, responseType, this);  // do this once on creation?
        CallService messageCallService = new CallService(service, args);
        String id = messageCallService.id;
        CallRecord callRecord = new CallRecord(responseHandler);
        calls.put(id, callRecord);
        client.send(messageCallService);
        return id;
    }
    
    public ResponseType poll(String id) {
        CallRecord call = calls.get(id);
        if (call.result != null)
            calls.remove(id);
        return call.result;
    }
    
    public ResponseType take(String id) throws InterruptedException {
        CallRecord call = calls.get(id);
        call.latch.await();
        calls.remove(id);
        return call.result;
    }
    
    public boolean verify() {
        // use discovery tools (rosapi) to confirm that
        // this service is actually running and that the data types
        // are correct. 
      return true;
    }
    
    private class CallRecord {
        public ResponseType result;
        public CountDownLatch latch;
        public MessageHandler<ResponseType> handler;
        
        public CallRecord(MessageHandler<ResponseType> handler) {
            this.result = null;
            this.latch = new CountDownLatch(1);
            this.handler = handler;
        }
    }
}
