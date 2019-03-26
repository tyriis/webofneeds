package won.node.camel.processor.fixed;

import java.net.URI;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.stereotype.Component;

import won.node.camel.processor.AbstractFromOwnerCamelProcessor;
import won.node.camel.processor.annotation.FixedMessageProcessor;
import won.node.camel.processor.general.OutboundMessageFactoryProcessor;
import won.protocol.exception.IllegalMessageForConnectionStateException;
import won.protocol.message.WonMessage;
import won.protocol.message.WonMessageBuilder;
import won.protocol.message.processor.camel.WonCamelConstants;
import won.protocol.message.processor.exception.MissingMessagePropertyException;
import won.protocol.message.processor.exception.WonMessageProcessingException;
import won.protocol.model.Connection;
import won.protocol.model.ConnectionState;
import won.protocol.vocabulary.WONMSG;

/**
 * User: syim Date: 02.03.2015
 */
@Component
@FixedMessageProcessor(direction = WONMSG.TYPE_FROM_OWNER_STRING, messageType = WONMSG.TYPE_CONNECTION_MESSAGE_STRING)
public class SendMessageFromOwnerProcessor extends AbstractFromOwnerCamelProcessor {

  public void process(final Exchange exchange) throws Exception {
    Message message = exchange.getIn();
    WonMessage wonMessage = (WonMessage) message.getHeader(WonCamelConstants.MESSAGE_HEADER);
    URI connectionUri = wonMessage.getSenderURI();
    if (connectionUri == null) {
      throw new MissingMessagePropertyException(URI.create(WONMSG.SENDER_PROPERTY.toString()));
    }
    Connection con = connectionRepository.findOneByConnectionURIForUpdate(connectionUri).get();
    if (con.getState() != ConnectionState.CONNECTED) {
      throw new IllegalMessageForConnectionStateException(connectionUri, "CONNECTION_MESSAGE", con.getState());
    }
    URI remoteMessageUri = wonNodeInformationService.generateEventURI(wonMessage.getReceiverNodeURI());

    if (wonMessage.getReceiverURI() == null) {
      // set the sender uri in the envelope TODO: TwoMsgs: do not set sender here
      wonMessage.addMessageProperty(WONMSG.RECEIVER_PROPERTY, con.getRemoteConnectionURI());
    }
    // add the information about the remote message to the locally stored one
    wonMessage.addMessageProperty(WONMSG.HAS_CORRESPONDING_REMOTE_MESSAGE, remoteMessageUri);
    // the persister will pick it up later

    // put the factory into the outbound message factory header. It will be used to
    // generate the outbound message
    // after the wonMessage has been processed and saved, to make sure that the
    // outbound message contains
    // all the data that we also store locally
    OutboundMessageFactory outboundMessageFactory = new OutboundMessageFactory(remoteMessageUri, con);
    exchange.getIn().setHeader(WonCamelConstants.OUTBOUND_MESSAGE_FACTORY_HEADER, outboundMessageFactory);
  }

  private class OutboundMessageFactory extends OutboundMessageFactoryProcessor {
    private Connection connection;

    public OutboundMessageFactory(URI messageURI, Connection connection) {
      super(messageURI);
      this.connection = connection;
    }

    @Override
    public WonMessage process(WonMessage message) throws WonMessageProcessingException {
      return WonMessageBuilder.setPropertiesForPassingMessageToRemoteNode(message, getMessageURI()).build();
    }
  }

}
