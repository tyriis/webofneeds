package won.node.camel.route.fixed;

import static org.apache.camel.builder.PredicateBuilder.*;
import static won.node.camel.WonNodeConstants.*;
import static won.node.camel.processor.WonCamelHelper.*;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.jena.query.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import won.node.camel.predicate.IsReactionAllowedPredicate;
import won.node.camel.predicate.ShouldCallSocketImplForMessagePredicate;
import won.protocol.message.WonMessage;
import won.protocol.message.WonMessageType;
import won.protocol.message.processor.camel.WonCamelConstants;
import won.protocol.util.RdfUtils;
import won.protocol.vocabulary.WONMSG;

/**
 * User: syim Date: 02.03.2015
 */
public class WonMessageRoutes extends RouteBuilder {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public void configure() throws Exception {
        from("direct:onExceptionFailResponder")
                        .routeId("direct:onExceptionFailResponder")
                        .onException(Exception.class)
                        /**/.log(LoggingLevel.WARN,
                                        simple("failure during direct:onExceptionFailResponder, ignoring. "
                                                        + "Exception message: ${exception.message}, "
                                                        + "Stacktrace: ${exception.stacktrace}").getText())
                        /**/.handled(true)
                        /**/.stop()
                        /**/.end()
                        .to("bean:failResponder")
                        .end();
        // checks message, throws exception if something is wrong
        from("direct:checkMessage")
                        .routeId("direct:checkMessage")
                        .onException(Exception.class)
                        /**/.handled(true)
                        /**/.to("direct:onExceptionFailResponder")
                        .end()
                        .transacted("PROPAGATION_REQUIRED")
                        .to("bean:wellformednessChecker")
                        .to("bean:uriNodePathChecker")
                        .to("bean:parentFinder")
                        .to("bean:uriInUseChecker")
                        .to("bean:signatureChecker");
        /**
         * Incoming from owner application: 1. process with direct:msgFromOwner (which
         * triggers other routes onCompletion) 2. send the message and the response back
         * to all owner applications registered for the sending atom
         */
        from("activemq:queue:" + FROM_OWNER_QUEUENAME + "?concurrentConsumers=5")
                        .routeId("activemq:queue:" + FROM_OWNER_QUEUENAME)
                        .setHeader(WonCamelConstants.DIRECTION_HEADER,
                                        constant(URI.create(WONMSG.FromOwnerString)))
                        .to("direct:msgFromOwner"); // after this, we have the response and the original in
                                                    // headers, and we know on behalf of which atom we're
                                                    // processing
        /**
         * Handles messages from the owner ("FROM_OWNER") or generated by the system
         * ("FROM_SYSTEM")
         */
        from("direct:msgFromOwner")
                        .routeId("direct:msgFromOwner")
                        .onException(Exception.class)
                        /**/.handled(true)
                        /**/.to("direct:onExceptionFailResponder")
                        .end()
                        // onException must be the first processor after onCompletion
                        /* onCompletion: executed after the whole route finishes successfully */
                        .onCompletion()
                        /**/.onCompleteOnly()
                        /**/.to("direct:msgFromOwner_respondToOwner")
                        .end() // onCompletion
                        /*
                         * start of the main route*
                         */
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .to("bean:wonMessageIntoCamelProcessor")
                        .to("direct:checkMessage")
                        // remember the connection state (if any)
                        .to("bean:connectionStateChangeBuilder")
                        // call the default implementation, which may alter the message.
                        .routingSlip(method("fixedMessageProcessorSlip"))
                        // depending on connection state change, make/delete derivations
                        .to("bean:dataDeriver")
                        .choice()
                        /**/.when(shouldCallSocketImplForMessage)
                        /**//**/.to("bean:socketTypeExtractor")
                        // find and execute the socket-specific processor
                        /**//**/.routingSlip(method("socketTypeSlip"))
                        /**/.endChoice()
                        .end()
                        .to("bean:successResponder") // now the successResponse is in the response header
                        .to("bean:persister"); // store the incoming message as well as the response
        from("direct:msgFromOwner_respondToOwner") // to owner
                        .routeId("direct:msgFromOwner_respondToOwner")
                        .onException(Exception.class)
                        /**/.log(simple("failure during direct:msgFromOwner_respondToOwner, not sending a response. Exception message: ${exception.message}, stacktrace: ${exception.stacktrace}")
                                        .getText())
                        /**/.handled(true)
                        /**/.stop()
                        .end()
                        .onCompletion()
                        /**/.onCompleteOnly()
                        /**/.to("direct:msgFromOwner_forwardToNode")
                        .end() // onCompletion
                        // send the response+echo to the owner
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .log(LoggingLevel.INFO, "Responding to owner")
                        .to("bean:responseRoutingInfoExtractor")
                        .bean(messageAndResponseIntoToSendHeader)
                        .to("direct:sendToOwner")
                        .to("bean:atomDeleter"); // if we are processing a delete, delete now (we needed the atom in the
                                                 // db for routing!)
        from("direct:msgFromOwner_forwardToNode") // to node
                        .routeId("direct:msgFromOwner_forwardToNode")
                        .onException(Exception.class)
                        /**/.log(simple("failure during direct:msgFromOwner_forwardToNode, not sending a response. Exception message: ${exception.message}, stacktrace: ${exception.stacktrace}")
                                        .getText())
                        /**/.handled(true)
                        /**/.stop()
                        .end()
                        .onCompletion()
                        /**/.onCompleteOnly()
                        /**/.to("direct:msgFromOwner_react")
                        .end()
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .choice()
                        /**/.when(and(causesOutgoingMessage, shouldSendFromOwnerToExternal))
                        /**//**/.log(LoggingLevel.INFO, "Forwarding message to remote node")
                        /**//**/.to("bean:routingInfoExtractor")
                        /**//**/.log(LoggingLevel.WARN,
                                        "TODO (message-refactoring): merge message dataset before sending")
                        /**//**/.bean(messageAndResponseIntoToSendHeader)
                        /**//**/.to("direct:sendToNode")
                        /**/.endChoice() // choice
                        .end();
        from("direct:msgFromOwner_react")
                        .routeId("direct:msgFromOwner_react")
                        .onException(Exception.class)
                        /**/.log(simple("failure during direct:msgFromOwner_react, not sending a response. Exception message: ${exception.message}, stacktrace: ${exception.stacktrace}")
                                        .getText())
                        /**/.handled(true)
                        /**/.stop()
                        .end()
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .choice()
                        /**/.when(isAllowedToReact)
                        /**//**/.log(LoggingLevel.INFO, "Reacting to message ")
                        // react to the message, if we don't want to suppress the reaction, which would
                        // be indicated by a header.
                        /**//**/.to("direct:reactToMessage")
                        /**/.endChoice() // choice
                        .end(); // choice
        /**
         * System messages: add timestamp, sign and then process as if it came from an
         * owner.
         */
        from("seda:SystemMessageIn?concurrentConsumers=5")
                        .routeId("seda:SystemMessageIn")
                        .to("bean:wonMessageIntoCamelProcessor")
                        .setHeader(WonCamelConstants.DIRECTION_HEADER,
                                        new URIConstant(URI.create(WONMSG.FromSystemString)))
                        .to("bean:timestampToMessageAdder")
                        .to("bean:referencesToMessageAdder")
                        .to("bean:signatureToMessageAdder")
                        // route to message processing logic
                        .to("direct:msgFromOwner");
        from("direct:reactToMessage")
                        .onException(Exception.class)
                        /**/.log(simple("failure during direct:reactToMessage, not sending a response. Exception message: ${exception.message}, stacktrace: ${exception.stacktrace}")
                                        .getText())
                        /**/.handled(true)
                        /**/.stop()
                        .end()
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .routeId("direct:reactToMessage")
                        .routingSlip(method("fixedMessageReactionProcessorSlip"));
        /**
         * Atom protocol, incoming
         */
        from("activemq:queue:" + FROM_NODE_QUEUENAME + "?concurrentConsumers=5")
                        .routeId("activemq:queue:" + FROM_NODE_QUEUENAME)
                        .choice() // (won nodes register the same way as owner applications do)
                        /**/.when(header("methodName").isEqualTo("register"))
                        /**//**/.to("bean:ownerManagementService?method=registerOwnerApplication")
                        /**/.when(header("methodName").isEqualTo("getEndpoints"))
                        /**//**/.to("bean:queueManagementService?method=getEndpointsForOwnerApplication")
                        /**/.otherwise()
                        /**//**/.to("bean:wonMessageIntoCamelProcessor")
                        /**//**/.to("direct:msgFromExternal")
                        /**/.endChoice()
                        .end();
        from("direct:msgFromExternal")
                        .routeId("direct:msgFromExternal")
                        .onException(Exception.class)
                        /**/.handled(true)
                        /**/.to("direct:onExceptionFailResponder")
                        .end()
                        .onCompletion()
                        /**/.onCompleteOnly()
                        /**/.to("direct:msgFromExternal_respondToNode")
                        .end() // onCompletion
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .setHeader(WonCamelConstants.DIRECTION_HEADER,
                                        new URIConstant(URI.create(WONMSG.FromExternal.getURI())))
                        .to("direct:checkMessage")
                        // remember the connection state (if any)
                        .to("bean:connectionStateChangeBuilder")
                        // call the default implementation, which may alter the message.
                        .routingSlip(method("fixedMessageProcessorSlip"))
                        // depending on connection state change, make/delete derivations
                        .to("bean:dataDeriver")
                        .to("bean:socketTypeExtractor")
                        .choice()
                        /**/.when(shouldCallSocketImplForMessage)
                        // find and execute the socket-specific processor
                        /**//**/.routingSlip(method("socketTypeSlip"))
                        /**/.endChoice()
                        .end()
                        .choice()
                        /**/.when(shouldRespond)
                        /**//**/.to("bean:successResponder") // now the successResponse may be the message header
                        /**/.endChoice()
                        .end()
                        .to("bean:persister"); // store the incoming message as well as the response, if there is any
        from("direct:msgFromExternal_respondToNode") // to node!
                        .routeId("direct:msgFromExternal_respondToNode")
                        .onException(Exception.class)
                        /**/.log(simple("failure during direct:msgFromExternal_respondToNode, not sending a response. Exception message: ${exception.message}, stacktrace: ${exception.stacktrace}")
                                        .getText())
                        /**/.handled(true)
                        /**/.stop()
                        .end()
                        .onCompletion()
                        /**/.onCompleteOnly()
                        /**/.to("direct:msgFromExternal_forwardToOwner")
                        .end() // onCompletion
                        .choice()
                        /**/.when(responseIsPresent) // we made a response, send it back to the node
                        /**//**/.log(LoggingLevel.INFO, "Sending response back to node")
                        /**//**/.to("bean:responseRoutingInfoExtractor")
                        /**//**/.bean(responseIntoMessageToSendHeader)
                        /**//**/.to("direct:sendToNode") // send response
                        /**/.endChoice() // choice
                        .end(); // choice
        from("direct:msgFromExternal_forwardToOwner") // to owner!
                        .routeId("direct:msgFromExternal_forwardToOwner")
                        // .onException(Exception.class)
                        // /**/.handled(true)
                        // /**/.stop()
                        // .end()
                        .onCompletion()
                        /**/.onCompleteOnly()
                        /**/.to("direct:msgFromExternal_react")
                        .end() // onCompletion
                        .log(LoggingLevel.INFO, "Sending message from external to owner")
                        .choice()
                        /**/.when(shouldSendExternalToOwner)
                        /**//**/.to("bean:routingInfoExtractor")
                        /**//**/.bean(messageAndResponseIntoToSendHeader)
                        /**//**/.to("direct:sendToOwner") // send response and echo as always
                        /**/.endChoice() // choice
                        .end(); // choice
        from("direct:msgFromExternal_react")
                        .routeId("direct:msgFromExternal_react")
                        .onException(Exception.class)
                        /**/.log(simple("failure during direct:msgFromExternal_react, not sending a response. Exception message: ${exception.message}, stacktrace: ${exception.stacktrace}")
                                        .getText())
                        /**/.handled(true)
                        /**/.stop()
                        .end()
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .choice()
                        /**/.when(isAllowedToReact)
                        // react to the message, if we don't want to suppress the reaction, which would
                        // be indicated by a header.
                        /**//**/.log(LoggingLevel.INFO, "Reacting to message from external")
                        /**//**/.to("direct:reactToMessage")
                        /**/.endChoice() // choice
                        .end(); // choice
        /**
         * Matcher protocol, incoming
         */
        from("activemq:queue:" + FROM_MATCHER_QUEUENAME + "?concurrentConsumers=5")
                        .onException(Exception.class)
                        /**/.log(simple("failure during activemq:queue:" + FROM_MATCHER_QUEUENAME
                                        + ", not sending a response. "
                                        + "Exception message: ${exception.message}, "
                                        + "Stacktrace: ${exception.stacktrace}").getText())
                        /**/.handled(true)
                        /**/.stop()
                        .end()
                        .transacted("PROPAGATION_REQUIRES_NEW")
                        .routeId("activemq:queue:" + FROM_MATCHER_QUEUENAME)
                        .to("bean:wonMessageIntoCamelProcessor")
                        .setHeader(WonCamelConstants.DIRECTION_HEADER,
                                        constant(URI.create(WONMSG.FromExternalString)))
                        .choice()
                        // we only handle hint messages
                        /**/.when(or(
                                        header(WonCamelConstants.MESSAGE_TYPE_HEADER).isEqualTo(
                                                        URI.create(WONMSG.AtomHintMessageString)),
                                        header(WonCamelConstants.MESSAGE_TYPE_HEADER).isEqualTo(
                                                        URI.create(WONMSG.SocketHintMessageString))))
                        // TODO as soon as Matcher can sign his messages, perform here
                        // .to("bean:wellformednessChecker") and .to("bean:signatureChecker")
                        /**//**/.to("bean:uriNodePathChecker")
                        /**//**/.to("bean:parentFinder")
                        /**//**/.to("bean:uriInUseChecker")
                        /**//**/.choice()
                        /**//**//**/.when(header(WonCamelConstants.MESSAGE_TYPE_HEADER)
                                        .isEqualTo(URI.create(WONMSG.AtomHintMessageString)))
                        /**//**//**//**/.to("bean:atomHintMessageProcessor?method=process")
                        /**//**//**/.when(header(WonCamelConstants.MESSAGE_TYPE_HEADER).isEqualTo(
                                        URI.create(WONMSG.SocketHintMessageString)))
                        /**//**//**//**/.to("bean:socketHintMessageProcessor?method=process")
                        // call the default implementation, which may alter the message.
                        /**//**//**/.endChoice()
                        /**//**/.choice()
                        /**//**//**/.when(not(header(WonCamelConstants.IGNORE_HINT_HEADER)))
                        /**//**//**//**/.to("bean:persister")
                        /**//**//**//**/.to("bean:routingInfoExtractor")
                        /**//**//**//**/.bean(messageIntoMessageToSendHeader)
                        /**//**//**//**/.to("direct:sendToOwner")
                        /**//**//**/.otherwise()
                        /**//**//**//**/.log(LoggingLevel.DEBUG,
                                        "suppressing sending of message to owner because the header '"
                                                        + WonCamelConstants.IGNORE_HINT_HEADER + "' is 'true'")
                        /**//**//**/.endChoice()
                        /**/.endChoice() // choice
                        .end(); // choice
        // sends the message in the won.messageToSend header to the owner(s)
        from("direct:sendToOwner")
                        .routeId("direct:sendToOwner")
                        .to("bean:toOwnerSender"); // makes a new exchange and sends it to
                                                   // direct:sendToOwnerApplications
        from("direct:sendToOwnerApplications")
                        .routeId("direct:sendToOwnerApplications")
                        .recipientList(header(WonCamelConstants.OWNER_APPLICATION_IDS_HEADER));
        // sends the message in the won.messageToSend header to the node
        from("direct:sendToNode")
                        .routeId("direct:sendToNode")
                        .to("bean:toNodeSender"); // sends the exchange to the endpoint that is connected to the other
                                                  // node (an activemq:queue endpoint) - if it is to be delivered
                                                  // locally, the message just goes to direct:msgFromExternal
        /**
         * Matcher protocol, outgoing
         */
        from("seda:MatcherProtocolOut?concurrentConsumers=5")
                        .routeId("seda:MatcherProtocolOut")
                        .transacted("PROPAGATION_NEVER")
                        .to("activemq:topic:" + TO_MATCHER_TOPIC);
    }

    private class URIConstant implements Expression {
        private URI uri;

        public URIConstant(final URI uri) {
            this.uri = uri;
        }

        @Override
        public <T> T evaluate(final Exchange exchange, final Class<T> type) {
            return type.cast(uri);
        }
    }

    /**
     * Matches if a message is found in the <code>MESSAGE_HEADER</code> or in the
     * <code>ORIGINAL_MESSAGE_HEADER</code> whose
     * <code>WonMessageType.causesOutgoingMessage()</code> method returns
     * <code>true</code>.
     * 
     * @author fkleedorfer
     */
    private Predicate causesOutgoingMessage = new CausesOutgoingMessage();

    public static class CausesOutgoingMessage implements Predicate {
        @Override
        public boolean matches(Exchange exchange) {
            WonMessage msg = (WonMessage) exchange.getIn().getHeader(WonCamelConstants.MESSAGE_HEADER);
            if (msg != null) {
                if (msg.getMessageTypeRequired().causesOutgoingMessage()) {
                    return true;
                }
            }
            return false;
        }
    }

    private Predicate isAllowedToReact = new IsReactionAllowedPredicate();
    private Predicate shouldCallSocketImplForMessage = new ShouldCallSocketImplForMessagePredicate();
    private Predicate shouldRespond = new Predicate() {
        public boolean matches(Exchange exchange) {
            WonMessage wonMessage = (WonMessage) exchange.getIn().getHeader(WonCamelConstants.MESSAGE_HEADER);
            WonMessageType messageType = wonMessage.getMessageType();
            return !messageType.isResponseMessage();
        }
    };
    private Predicate shouldSendExternalToOwner = new Predicate() {
        public boolean matches(Exchange exchange) {
            Boolean suppress = (Boolean) exchange.getIn().getHeader(WonCamelConstants.SUPPRESS_MESSAGE_TO_OWNER_HEADER);
            if (suppress != null && suppress == true) {
                return false;
            }
            return true;
        }
    };
    private Predicate shouldSendFromOwnerToExternal = new Predicate() {
        public boolean matches(Exchange exchange) {
            Boolean suppress = (Boolean) exchange.getIn().getHeader(WonCamelConstants.SUPPRESS_MESSAGE_TO_NODE_HEADER);
            if (suppress != null && suppress == true) {
                return false;
            }
            return true;
        }
    };
    private Predicate responseIsPresent = new Predicate() {
        public boolean matches(Exchange exchange) {
            WonMessage response = (WonMessage) exchange.getIn().getHeader(WonCamelConstants.RESPONSE_HEADER);
            return response != null && response.getMessageTypeRequired().isResponseMessage();
        }
    };
    private Processor responseIntoMessageToSendHeader = new Processor() {
        public void process(Exchange exchange) throws Exception {
            putMessageToSend(exchange, getResponseRequired(exchange));
        }
    };
    private Processor messageIntoMessageToSendHeader = new Processor() {
        public void process(Exchange exchange) throws Exception {
            putMessageToSend(exchange, getMessageRequired(exchange));
        }
    };
    private Processor messageAndResponseIntoToSendHeader = new Processor() {
        public void process(Exchange exchange) throws Exception {
            WonMessage msg = getMessageRequired(exchange);
            Optional<WonMessage> resp = getResponse(exchange);
            if (resp.isPresent()) {
                Dataset ds = msg.getCompleteDataset();
                RdfUtils.addDatasetToDataset(ds, resp.get().getCompleteDataset());
                msg = WonMessage.of(ds);
            }
            putMessageToSend(exchange, msg);
        }
    };
}
