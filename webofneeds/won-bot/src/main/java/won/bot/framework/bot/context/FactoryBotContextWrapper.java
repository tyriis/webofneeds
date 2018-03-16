package won.bot.framework.bot.context;

import won.bot.framework.eventbot.action.impl.factory.model.Precondition;
import won.bot.framework.eventbot.action.impl.factory.model.Proposal;

import java.net.URI;
import java.util.List;

public class FactoryBotContextWrapper extends BotContextWrapper {
    private final String factoryListName = getBotName() + ":factoryList";
    private final String factoryInternalIdName = getBotName() + ":factoryInternalId";
    private final String factoryOfferToFactoryNeedMapName = getBotName() + ":factoryOfferToFactoryNeedMap";
    private final String connectionToPreconditionListMapName = getBotName() + ":connectionToPreconditionListMap";
    private final String connectionToProposalListMapName = getBotName() + ":connectionToProposalListMap";
    private final String preconditionToConnectionMapName = getBotName() + ":preconditionToConnectionMap";
    private final String preconditionToProposalListMapName = getBotName() + ":precondtionToProposalListMap";
    private final String preconditionConversationStateMapName = getBotName() + ":preconditionConversationStateMap";
    private final String proposalToPreconditionListMapName = getBotName() + ":proposalToPreconditionListMap";
    private final String proposalToConnectionMapName = getBotName() + ":proposalToConnectionMap";

    public FactoryBotContextWrapper(BotContext botContext, String botName) {
        super(botContext, botName);
    }

    public String getFactoryListName() {
        return factoryListName;
    }

    public boolean isFactoryNeed(URI uri) {
        return getBotContext().isInNamedNeedUriList(uri, factoryListName);
    }

    public URI getURIFromInternal(URI uri) {
        return (URI) getBotContext().loadFromObjectMap(factoryInternalIdName, uri.toString());
    }

    public void addInternalIdToUriReference(URI internalUri, URI uri){
        getBotContext().saveToObjectMap(factoryInternalIdName, internalUri.toString(), uri);
    }

    public URI getFactoryNeedURIFromOffer(URI offerURI) {
        return (URI) getBotContext().loadFromObjectMap(factoryOfferToFactoryNeedMapName, offerURI.toString());
    }

    public void addFactoryNeedURIOfferRelation(URI offerURI, URI factoryNeedURI){
        getBotContext().saveToObjectMap(factoryOfferToFactoryNeedMapName, offerURI.toString(), factoryNeedURI);
    }

    /**
     * @param preconditionURI to retrieve the state from
     * @return the saved state of the precondition, null if the state was never saved before (undeterminable)
     */
    public Boolean getPreconditionConversationState(String preconditionURI) {
        return (Boolean) getBotContext().loadFromObjectMap(preconditionConversationStateMapName, preconditionURI);
    }

    /**
     * Saves the state of the precondition
     * @param preconditionURI to save the state of
     * @param state to save
     */
    public void addPreconditionConversationState(String preconditionURI, boolean state) {
        getBotContext().saveToObjectMap(preconditionConversationStateMapName, preconditionURI, state);
    }

    /**
     * Adds one or more preconditions to the given connection Uri ListMap
     * @param connectionURI a single connectionUri to store as the key of the ListMap
     * @param preconditionURI one or more preconditionUris that should be linked with the connection
     */
    public void addConnectionPrecondition(URI connectionURI, String... preconditionURI) {
        getBotContext().addToListMap(connectionToPreconditionListMapName, connectionURI.toString(), preconditionURI);

        for(String preconUri : preconditionURI) {
            getBotContext().saveToObjectMap(preconditionToConnectionMapName, preconUri, connectionURI);
        }
    }

    /**
     * Returns a List of All saved Precondition URIS for the connection
     * @param connectionURI uri of the connection to retrieve the preconditionList of
     * @return List of all URI-Strings of preconditions save for the given connectionURI
     */
    public List<String> getPreconditionsForConnectionUri(URI connectionURI) {
        return getPreconditionsForConnectionUri(connectionURI.toString());
    }

    /**
     * Returns a List of All saved Precondition URIS for the connection
     * @param connectionURI string of the uri of the connection to retrieve the preconditionList of
     * @return List of all URI-Strings of preconditions save for the given connectionURI
     */
    public List<String> getPreconditionsForConnectionUri(String connectionURI) {
        return (List<String>)(List<?>) getBotContext().loadFromListMap(connectionToPreconditionListMapName, connectionURI);
    }

    /**
     * @param preconditionURI the uri to retrieve the connection uri for
     * @return Returns the connection uri for a certain precondition uri
     */
    public URI getConnectionURIFromPreconditionURI(String preconditionURI) {
        return (URI) getBotContext().loadFromObjectMap(preconditionToConnectionMapName, preconditionURI);
    }

    public void addPreconditionProposalRelation(Precondition precondition, Proposal proposal) {
        getBotContext().addToListMap(preconditionToProposalListMapName, precondition.getUri(), proposal);
        getBotContext().addToListMap(proposalToPreconditionListMapName, proposal.getUri().toString(), precondition);
    }

    public boolean hasPreconditionProposalRelation(String preconditionURI, String proposalURI) {
        return getPreconditionsForProposalUri(proposalURI).contains(preconditionURI);
    }

    public List<Proposal> getProposalsForPreconditionUri(String preconditionURI){
        return (List<Proposal>)(List<?>) getBotContext().loadFromListMap(preconditionToProposalListMapName, preconditionURI);
    }

    public List<Proposal> getProposalsForConnectionUri(String connectionURI){
        return (List<Proposal>)(List<?>) getBotContext().loadFromListMap(connectionToPreconditionListMapName, connectionURI);
    }

    /**
     * Returns a List of All saved Precondition URIS for the proposal
     * @param proposalURI uri of the proposal to retrieve the preconditionList of
     * @return List of all URI-Strings of preconditions save for the given connectionURI
     */
    public List<String> getPreconditionsForProposalUri(String proposalURI) {
        return (List<String>)(List<?>) getBotContext().loadFromListMap(connectionToPreconditionListMapName, proposalURI.toString());
    }

    /**
     * Removes all the stored entries for a given connectionURI
     * @param connectionURI the string of the connection URI that is to be removed from here
     */
    public void removeConnectionReferences(String connectionURI) {
        //TODO: REMOVE ALL UNUSED VALUES (TO BE DETERMINED)
        getPreconditionsForConnectionUri(connectionURI).forEach(this::removePreconditionReferences);
        getProposalsForConnectionUri(connectionURI).forEach(this::removeProposalReferences);

        getBotContext().removeFromListMap(connectionToPreconditionListMapName, connectionURI);
        getBotContext().removeFromListMap(connectionToProposalListMapName, connectionURI);
    }


    /**
     * Removes All the stored entries in all Maps Lists or MapList for the given Proposal
     * @param proposal to be removed
     */
    public void removeProposalReferences(Proposal proposal) {
        removeProposalReferences(proposal.getUri());
    }

    /**
     * Removes All the stored entries in all Maps Lists or MapList for the given ProposalURI
     * @param proposalURI to be removed
     */
    public void removeProposalReferences(URI proposalURI) {
        removeProposalReferences(proposalURI.toString());
    }

    /**
     * Removes All the stored entries in all Maps Lists or MapList for the given ProposalURI
     * @param proposalURI the string of the proposalURI to be removed
     */
    public void removeProposalReferences(String proposalURI) {
        getBotContext().removeFromObjectMap(proposalToConnectionMapName, proposalURI);
        getBotContext().removeLeavesFromListMap(connectionToProposalListMapName, proposalURI);
        getBotContext().removeFromListMap(proposalToPreconditionListMapName, proposalURI);
        getBotContext().removeLeavesFromListMap(preconditionToProposalListMapName, proposalURI);
    }

    public void removePreconditionReferences(String preconditionURI) {
        getBotContext().removeFromObjectMap(preconditionConversationStateMapName, preconditionURI);
        getBotContext().removeLeavesFromListMap(connectionToPreconditionListMapName, preconditionURI);
        getBotContext().removeFromObjectMap(preconditionToConnectionMapName, preconditionURI);
        getBotContext().removeFromObjectMap(preconditionToProposalListMapName, preconditionURI);
        getBotContext().removeLeavesFromListMap(proposalToPreconditionListMapName, preconditionURI);
    }
}
