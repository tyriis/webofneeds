import won from "../won-es6.js";
import Immutable from "immutable";
import angular from "angular";
import chatTextFieldSimpleModule from "./chat-textfield-simple.js";
import connectionMessageModule from "./messages/connection-message.js";
import postContentMessageModule from "./messages/post-content-message.js";
import petrinetStateModule from "./petrinet-state.js";
import connectionHeaderModule from "./connection-header.js";
import labelledHrModule from "./labelled-hr.js";
import connectionContextDropdownModule from "./connection-context-dropdown.js";
import { connect2Redux } from "../won-utils.js";
import { attach, delay, getIn } from "../utils.js";
import { isWhatsAroundNeed, isWhatsNewNeed } from "../need-utils.js";
import {
  fetchAgreementProtocolUris,
  fetchPetriNetUris,
  fetchMessage,
} from "../won-message-utils.js";
import { actionCreators } from "../actions/actions.js";
import {
  getConnectionUriFromRoute,
  getOwnedNeedByConnectionUri,
} from "../selectors/general-selectors.js";
import {
  getAgreementMessagesByConnectionUri,
  getCancellationPendingMessagesByConnectionUri,
  getProposalMessagesByConnectionUri,
  getUnreadMessagesByConnectionUri,
} from "../selectors/message-selectors.js";
import autoresizingTextareaModule from "../directives/textarea-autogrow.js";
import { classOnComponentRoot } from "../cstm-ng-utils.js";

import "style/_group-post-messages.scss";
import "style/_rdflink.scss";

const serviceDependencies = ["$ngRedux", "$scope", "$element"];

function genComponentConf() {
  let template = `
        <div class="gpm__header" ng-if="self.showChatData">
            <a class="gpm__header__back clickable"
               ng-click="self.router__stateGoCurrent({connectionUri : undefined})">
                <svg style="--local-primary:var(--won-primary-color);"
                     class="gpm__header__back__icon clickable">
                    <use xlink:href="#ico36_backarrow" href="#ico36_backarrow"></use>
                </svg>
            </a>
            <won-connection-header
                connection-uri="self.connectionUri"
                timestamp="self.lastUpdateTimestamp"
                hide-image="::false">
            </won-connection-header>
            <won-connection-context-dropdown show-petri-net-data-field="::self.showPetriNetDataField()" show-agreement-data-field="::self.showAgreementDataField()"></won-connection-context-dropdown>
        </div>
        <div
          class="gpm__content">
            <div class="gpm__content__unreadindicator"
              ng-if="self.unreadMessageCount && (!self._snapBottom || !self.showChatView)">
              <div class="gpm__content__unreadindicator__content won-button--filled red"
                ng-click="self.goToUnreadMessages()">
                {{self.unreadMessageCount}} unread Messages
              </div>
            </div>
            <won-post-content-message
              class="won-cm--left"
              ng-if="self.showChatData && !self.multiSelectType && self.nonOwnedNeedUri"
              post-uri="self.nonOwnedNeedUri">
            </won-post-content-message>
            <div class="gpm__content__loadspinner"
                ng-if="self.isProcessingLoadingMessages || (self.showAgreementData && self.isProcessingLoadingAgreementData) || (self.showPetriNetData && self.isProcessingLoadingPetriNetData && !self.hasPetriNetData)">
                <svg class="hspinner">
                  <use xlink:href="#ico_loading_anim" href="#ico_loading_anim"></use>
                </svg>
            </div>
            <button class="gpm__content__loadbutton won-button--outlined thin red"
                ng-if="!self.isSuggested && self.showChatData && !self.isProcessingLoadingMessages && !self.allMessagesLoaded"
                ng-click="self.loadPreviousMessages()">
                Load previous messages
            </button>

            <!-- CHATVIEW SPECIFIC CONTENT START-->
            <won-connection-message
                ng-if="self.showChatData"
                ng-click="self.multiSelectType && self.selectMessage(msg)"
                ng-repeat="msg in self.sortedMessages"
                connection-uri="self.connectionUri"
                message-uri="msg.get('uri')">
            </won-connection-message>
            <!-- CHATVIEW SPECIFIC CONTENT END-->

            <a class="rdflink clickable"
               ng-if="self.shouldShowRdf"
               target="_blank"
               href="{{ self.connectionUri }}">
                    <svg class="rdflink__small">
                        <use xlink:href="#rdf_logo_1" href="#rdf_logo_1"></use>
                    </svg>
                    <span class="rdflink__label">Connection</span>
            </a>
        </div>
        <div class="gpm__footer" ng-if="!self.showPetriNetData && self.isConnected">
            <chat-textfield-simple
                class="gpm__footer__chattexfield"
                placeholder="self.shouldShowRdf? 'Enter TTL...' : 'Your message...'"
                submit-button-label="self.shouldShowRdf? 'Send&#160;RDF' : 'Send'"
                on-submit="self.send(value, additionalContent, referencedContent, self.shouldShowRdf)"
                help-text="self.shouldShowRdf? self.rdfTextfieldHelpText : ''"
                allow-empty-submit="::false"
                allow-details="!self.shouldShowRdf"
                is-code="self.shouldShowRdf? 'true' : ''"
            >
            </chat-textfield-simple>
        </div>
        <div class="gpm__footer" ng-if="!self.showPetriNetData && !self.multiSelectType && self.isSentRequest">
            Waiting for the Group Administrator to accept your request.
        </div>

        <div class="gpm__footer" ng-if="!self.showPetriNetData && !self.multiSelectType && self.isReceivedRequest">
            <chat-textfield-simple
                class="gpm__footer__chattexfield"
                placeholder="::'Message (optional)'"
                on-submit="::self.openRequest(value)"
                allow-details="::false"
                allow-empty-submit="::true"
                submit-button-label="::'Accept&#160;Invite'"
            >
            </chat-textfield-simple>
            <won-labelled-hr label="::'Or'" class="gpm__footer__labelledhr"></won-labelled-hr>
            <button class="gpm__footer__button won-button--filled black" ng-click="self.closeConnection()">
                Decline
            </button>
        </div>
        <div class="gpm__footer" ng-if="!self.showPetriNetData && !self.multiSelectType && self.isSuggested">
            <chat-textfield-simple
                placeholder="::'Message (optional)'"
                on-submit="::self.sendRequest(value, selectedPersona)"
                allow-details="::false"
                allow-empty-submit="::true"
                show-personas="self.isOwnedNeedWhatsX"
                submit-button-label="::'Ask&#160;to&#160;Join'"
            >
            </chat-textfield-simple>
            <won-labelled-hr label="::'Or'" class="gpm__footer__labelledhr"></won-labelled-hr>
            <button class="gpm__footer__button won-button--filled black" ng-click="self.closeConnection(true)">
                Bad match - remove!
            </button>
        </div>
    `;

  class Controller {
    constructor(/* arguments = dependency injections */) {
      attach(this, serviceDependencies, arguments);
      window.pm4dbg = this;

      this.rdfTextfieldHelpText =
        "Expects valid turtle. " +
        `<${won.WONMSG.uriPlaceholder.event}> will ` +
        "be replaced by the uri generated for this message. " +
        "Use it, so your TTL can be found when parsing the messages. " +
        "See `won.defaultTurtlePrefixes` " +
        "for prefixes that will be added automatically. E.g." +
        `\`<${
          won.WONMSG.uriPlaceholder.event
        }> won:hasTextMessage "hello world!". \``;

      this.scrollContainer().addEventListener("scroll", e => this.onScroll(e));

      const selectFromState = state => {
        const connectionUri = getConnectionUriFromRoute(state);
        const ownedNeed = getOwnedNeedByConnectionUri(state, connectionUri);
        const connection =
          ownedNeed && ownedNeed.getIn(["connections", connectionUri]);
        const isOwnedNeedWhatsX =
          this.ownedNeed &&
          (isWhatsAroundNeed(this.ownedNeed) || isWhatsNewNeed(this.ownedNeed));
        const nonOwnedNeedUri = connection && connection.get("remoteNeedUri");
        const nonOwnedNeed =
          nonOwnedNeedUri && state.getIn(["needs", nonOwnedNeedUri]);
        const chatMessages =
          connection &&
          connection.get("messages") &&
          connection.get("messages").filter(msg => !msg.get("forwardMessage"));
        const allMessagesLoaded =
          chatMessages &&
          chatMessages.filter(
            msg => msg.get("messageType") === won.WONMSG.connectMessage
          ).size > 0;

        const agreementData = connection && connection.get("agreementData");
        const petriNetData = connection && connection.get("petriNetData");

        const agreementMessages = getAgreementMessagesByConnectionUri(
          state,
          connectionUri
        );
        const cancellationPendingMessages = getCancellationPendingMessagesByConnectionUri(
          state,
          connectionUri
        );
        const proposalMessages = getProposalMessagesByConnectionUri(
          state,
          connectionUri
        );

        let sortedMessages = chatMessages && chatMessages.toArray();
        sortedMessages &&
          sortedMessages.sort(function(a, b) {
            const aDate = a.get("date");
            const bDate = b.get("date");

            const aTime = aDate && aDate.getTime();
            const bTime = bDate && bDate.getTime();

            return aTime - bTime;
          });

        const unreadMessages = getUnreadMessagesByConnectionUri(
          state,
          connectionUri
        );

        const chatMessagesWithUnknownState =
          chatMessages &&
          chatMessages.filter(msg => !msg.get("isMessageStatusUpToDate"));

        return {
          ownedNeed,
          nonOwnedNeed,
          nonOwnedNeedUri,
          connectionUri,
          connection,
          isOwnedNeedWhatsX,

          sortedMessages: sortedMessages,
          chatMessages,
          chatMessagesWithUnknownState,
          unreadMessageCount: unreadMessages && unreadMessages.size,
          isProcessingLoadingMessages:
            connection &&
            getIn(state, [
              "process",
              "connections",
              connectionUri,
              "loadingMessages",
            ]),
          isProcessingLoadingAgreementData:
            connection &&
            getIn(state, [
              "process",
              "connections",
              connectionUri,
              "agreementData",
              "loading",
            ]),
          isProcessingLoadingPetriNetData:
            connection &&
            getIn(state, [
              "process",
              "connections",
              connectionUri,
              "petriNetData",
              "loading",
            ]),
          showAgreementData: connection && connection.get("showAgreementData"),
          showPetriNetData: connection && connection.get("showPetriNetData"),
          showChatData:
            connection &&
            !(
              connection.get("showAgreementData") ||
              connection.get("showPetriNetData")
            ),
          agreementData,
          petriNetData,
          petriNetDataArray: petriNetData && petriNetData.toArray(),
          agreementDataLoaded:
            agreementData &&
            getIn(state, [
              "process",
              "connections",
              connectionUri,
              "agreementData",
              "loaded",
            ]),
          petriNetDataLoaded:
            petriNetData &&
            getIn(state, [
              "process",
              "connections",
              connectionUri,
              "petriNetData",
              "loaded",
            ]),
          multiSelectType: connection && connection.get("multiSelectType"),
          lastUpdateTimestamp: connection && connection.get("lastUpdateDate"),
          isSentRequest:
            connection && connection.get("state") === won.WON.RequestSent,
          isReceivedRequest:
            connection && connection.get("state") === won.WON.RequestReceived,
          isConnected:
            connection && connection.get("state") === won.WON.Connected,
          isSuggested:
            connection && connection.get("state") === won.WON.Suggested,
          debugmode: won.debugmode,
          shouldShowRdf: state.getIn(["view", "showRdf"]),
          // if the connect-message is here, everything else should be as well
          allMessagesLoaded,
          hasAgreementMessages: agreementMessages && agreementMessages.size > 0,
          hasPetriNetData: petriNetData && petriNetData.size > 0,
          agreementMessagesArray:
            agreementMessages && agreementMessages.toArray(),
          hasProposalMessages: proposalMessages && proposalMessages.size > 0,
          proposalMessagesArray: proposalMessages && proposalMessages.toArray(),
          hasCancellationPendingMessages:
            cancellationPendingMessages && cancellationPendingMessages.size > 0,
          cancellationPendingMessagesArray:
            cancellationPendingMessages &&
            cancellationPendingMessages.toArray(),
          connectionOrNeedsLoading:
            !connection ||
            !nonOwnedNeed ||
            !ownedNeed ||
            getIn(state, [
              "process",
              "needs",
              ownedNeed.get("uri"),
              "loading",
            ]) ||
            getIn(state, [
              "process",
              "needs",
              nonOwnedNeed.get("uri"),
              "loading",
            ]) ||
            getIn(state, ["process", "connections", connectionUri, "loading"]),
        };
      };

      connect2Redux(selectFromState, actionCreators, [], this);

      this._snapBottom = true; //Don't snap to bottom immediately, because this scrolls the whole page... somehow?

      this.$scope.$watchGroup(["self.connection"], () => {
        this.ensureMessagesAreLoaded();
        this.ensureAgreementDataIsLoaded();
        this.ensurePetriNetDataIsLoaded();
        this.ensureMessageStateIsUpToDate();
      });

      this.$scope.$watch(
        () => this.sortedMessages && this.sortedMessages.length, // trigger if there's messages added (or removed)
        () =>
          delay(0).then(() =>
            // scroll to bottom directly after rendering, if snapped
            this.updateScrollposition()
          )
      );

      classOnComponentRoot(
        "won-is-loading",
        () => this.connectionOrNeedsLoading,
        this
      );
    }

    ensureMessagesAreLoaded() {
      delay(0).then(() => {
        // make sure latest messages are loaded
        const INITIAL_MESSAGECOUNT = 15;
        if (
          this.connection &&
          !this.isProcessingLoadingMessages &&
          !(this.allMessagesLoaded || this.connection.get("messages").size > 0)
        ) {
          this.connections__showLatestMessages(
            this.connection.get("uri"),
            INITIAL_MESSAGECOUNT
          );
        }
      });
    }

    ensurePetriNetDataIsLoaded(forceFetch = false) {
      delay(0).then(() => {
        if (
          forceFetch ||
          (this.isConnected &&
            !this.isProcessingLoadingPetriNetData &&
            !this.petriNetDataLoaded)
        ) {
          const connectionUri = this.connection && this.connection.get("uri");

          this.connections__setLoadingPetriNetData({
            connectionUri: connectionUri,
            loadingPetriNetData: true,
          });

          fetchPetriNetUris(connectionUri)
            .then(response => {
              const petriNetData = {};

              response.forEach(entry => {
                if (entry.processURI) {
                  petriNetData[entry.processURI] = entry;
                }
              });

              const petriNetDataImm = Immutable.fromJS(petriNetData);

              this.connections__updatePetriNetData({
                connectionUri: connectionUri,
                petriNetData: petriNetDataImm,
              });
            })
            .catch(error => {
              console.error("Error:", error);
              this.connections__setLoadingPetriNetData({
                connectionUri: connectionUri,
                loadingPetriNetData: false,
              });
            });
        }
      });
    }

    ensureAgreementDataIsLoaded(forceFetch = false) {
      delay(0).then(() => {
        if (
          forceFetch ||
          (this.isConnected &&
            !this.isProcessingLoadingAgreementData &&
            !this.agreementDataLoaded)
        ) {
          this.connections__setLoadingAgreementData({
            connectionUri: this.connectionUri,
            loadingAgreementData: true,
          });
          fetchAgreementProtocolUris(this.connection.get("uri"))
            .then(response => {
              let proposedMessageUris = [];
              const pendingProposals = response.pendingProposals;

              if (pendingProposals) {
                pendingProposals.forEach(prop => {
                  if (prop.proposes) {
                    proposedMessageUris = proposedMessageUris.concat(
                      prop.proposes
                    );
                  }
                });
              }

              const agreementData = Immutable.fromJS({
                agreementUris: Immutable.Set(response.agreementUris),
                pendingProposalUris: Immutable.Set(
                  response.pendingProposalUris
                ),
                acceptedCancellationProposalUris: Immutable.Set(
                  response.acceptedCancellationProposalUris
                ),
                cancellationPendingAgreementUris: Immutable.Set(
                  response.cancellationPendingAgreementUris
                ),
                pendingCancellationProposalUris: Immutable.Set(
                  response.pendingCancellationProposalUris
                ),
                cancelledAgreementUris: Immutable.Set(
                  response.cancelledAgreementUris
                ),
                rejectedMessageUris: Immutable.Set(
                  response.rejectedMessageUris
                ),
                retractedMessageUris: Immutable.Set(
                  response.retractedMessageUris
                ),
                proposedMessageUris: Immutable.Set(proposedMessageUris),
                claimedMessageUris: Immutable.Set(response.claimedMessageUris),
              });

              this.connections__updateAgreementData({
                connectionUri: this.connectionUri,
                agreementData: agreementData,
              });

              //Retrieve all the relevant messages
              agreementData.map((uriList, key) =>
                uriList.map(uri => this.addMessageToState(uri, key))
              );
            })
            .catch(error => {
              console.error("Error:", error);
              this.connections__setLoadingAgreementData({
                connectionUri: this.connectionUri,
                loadingAgreementData: false,
              });
            });
        }
      });
    }

    ensureMessageStateIsUpToDate() {
      delay(0).then(() => {
        if (
          this.isConnected &&
          !this.isProcessingLoadingAgreementData &&
          !this.isProcessingLoadingMessages &&
          this.agreementDataLoaded &&
          this.chatMessagesWithUnknownState &&
          this.chatMessagesWithUnknownState.size > 0
        ) {
          console.debug(
            "Ensure Message Status is up-to-date for: ",
            this.chatMessagesWithUnknownState.size,
            " Messages"
          );
          this.chatMessagesWithUnknownState.forEach(msg => {
            let messageStatus = msg && msg.get("messageStatus");
            const msgUri = msg.get("uri");
            const remoteMsgUri = msg.get("remoteUri");

            const acceptedUris =
              this.agreementData && this.agreementData.get("agreementUris");
            const rejectedUris =
              this.agreementData &&
              this.agreementData.get("rejectedMessageUris");
            const retractedUris =
              this.agreementData &&
              this.agreementData.get("retractedMessageUris");
            const cancelledUris =
              this.agreementData &&
              this.agreementData.get("cancelledAgreementUris");
            const cancellationPendingUris =
              this.agreementData &&
              this.agreementData.get("cancellationPendingAgreementUris");
            const claimedUris =
              this.agreementData &&
              this.agreementData.get("claimedMessageUris"); //TODO not sure if this is correct
            const proposedUris =
              this.agreementData &&
              this.agreementData.get("proposedMessageUris"); //TODO not sure if this is correct

            const isProposed = messageStatus && messageStatus.get("isProposed");
            const isClaimed = messageStatus && messageStatus.get("isClaimed");
            const isAccepted = messageStatus && messageStatus.get("isAccepted");
            const isRejected = messageStatus && messageStatus.get("isRejected");
            const isRetracted =
              messageStatus && messageStatus.get("isRetracted");
            const isCancelled =
              messageStatus && messageStatus.get("isCancelled");
            const isCancellationPending =
              messageStatus && messageStatus.get("isCancellationPending");

            const isOldProposed =
              proposedUris &&
              !!(proposedUris.get(msgUri) || proposedUris.get(remoteMsgUri));
            const isOldClaimed =
              claimedUris &&
              !!(claimedUris.get(msgUri) || claimedUris.get(remoteMsgUri));
            const isOldAccepted =
              acceptedUris &&
              !!(acceptedUris.get(msgUri) || acceptedUris.get(remoteMsgUri));
            const isOldRejected =
              rejectedUris &&
              !!(rejectedUris.get(msgUri) || rejectedUris.get(remoteMsgUri));
            const isOldRetracted =
              retractedUris &&
              !!(retractedUris.get(msgUri) || retractedUris.get(remoteMsgUri));
            const isOldCancelled =
              cancelledUris &&
              !!(cancelledUris.get(msgUri) || cancelledUris.get(remoteMsgUri));
            const isOldCancellationPending =
              cancellationPendingUris &&
              !!(
                cancellationPendingUris.get(msgUri) ||
                cancellationPendingUris.get(remoteMsgUri)
              );

            messageStatus = messageStatus
              .set("isProposed", isProposed || isOldProposed)
              .set("isClaimed", isClaimed || isOldClaimed)
              .set("isAccepted", isAccepted || isOldAccepted)
              .set("isRejected", isRejected || isOldRejected)
              .set("isRetracted", isRetracted || isOldRetracted)
              .set("isCancelled", isCancelled || isOldCancelled)
              .set(
                "isCancellationPending",
                isCancellationPending || isOldCancellationPending
              );

            this.messages__updateMessageStatus({
              messageUri: msgUri,
              connectionUri: this.connectionUri,
              needUri: this.ownedNeed.get("uri"),
              messageStatus: messageStatus,
            });
          });
        }
      });
    }

    loadPreviousMessages() {
      delay(0).then(() => {
        const MORE_MESSAGECOUNT = 5;
        if (this.connection && !this.isProcessingLoadingMessages) {
          this.connections__showMoreMessages(
            this.connection.get("uri"),
            MORE_MESSAGECOUNT
          );
        }
      });
    }

    goToUnreadMessages() {
      if (this.showAgreementData) {
        this.setShowAgreementData(false);
      }
      if (this.showPetriNetData) {
        this.setShowPetriNetData(false);
      }
      this.snapToBottom();
    }

    snapToBottom() {
      this._snapBottom = true;
      this.scrollToBottom();
    }
    unsnapFromBottom() {
      this._snapBottom = false;
    }
    updateScrollposition() {
      if (this._snapBottom) {
        this.scrollToBottom();
      }
    }
    scrollToBottom() {
      this._programmaticallyScrolling = true;

      this.scrollContainer().scrollTop = this.scrollContainer().scrollHeight;
    }
    onScroll() {
      if (!this._programmaticallyScrolling) {
        //only unsnap if the user scrolled themselves
        this.unsnapFromBottom();
      }

      const sc = this.scrollContainer();
      const isAtBottom = sc.scrollTop + sc.offsetHeight >= sc.scrollHeight;
      if (isAtBottom) {
        this.snapToBottom();
      }

      this._programmaticallyScrolling = false;
    }
    scrollContainer() {
      if (!this._scrollContainer) {
        this._scrollContainer = this.$element[0].querySelector(".gpm__content");
      }
      return this._scrollContainer;
    }

    send(chatMessage, additionalContent, referencedContent, isTTL = false) {
      this.setShowAgreementData(false);
      this.view__hideAddMessageContent();

      const trimmedMsg = chatMessage.trim();
      if (trimmedMsg || additionalContent || referencedContent) {
        this.connections__sendChatMessage(
          trimmedMsg,
          additionalContent,
          referencedContent,
          this.connection.get("uri"),
          isTTL
        );
      }
    }

    showAgreementDataField() {
      this.setShowPetriNetData(false);
      this.setShowAgreementData(true);
    }

    showPetriNetDataField() {
      this.setShowAgreementData(false);
      this.setShowPetriNetData(true);
    }

    setShowAgreementData(value) {
      this.connections__showAgreementData({
        connectionUri: this.connectionUri,
        showAgreementData: value,
      });
    }

    setShowPetriNetData(value) {
      this.connections__showPetriNetData({
        connectionUri: this.connectionUri,
        showPetriNetData: value,
      });
    }

    addMessageToState(eventUri, key) {
      const ownedNeedUri = this.ownedNeed.get("uri");
      return fetchMessage(ownedNeedUri, eventUri).then(response => {
        won.wonMessageFromJsonLd(response).then(msg => {
          if (msg.isFromOwner() && msg.getReceiverNeed() === ownedNeedUri) {
            /*if we find out that the receiverneed of the crawled event is actually our
              need we will call the method again but this time with the correct eventUri
            */
            this.addMessageToState(msg.getRemoteMessageUri(), key);
          } else {
            //If message isnt in the state we add it
            if (!this.chatMessages.get(eventUri)) {
              this.messages__processAgreementMessage(msg);
            }
          }
        });
      });
    }

    openRequest(message) {
      this.connections__open(this.connectionUri, message);
    }

    sendRequest(message, persona) {
      if (!this.connection || this.isOwnedNeedWhatsX) {
        this.router__stateGoResetParams("connections");

        if (this.isOwnedNeedWhatsX) {
          //Close the connection if there was a present connection for a whatsaround need
          this.connections__close(this.connectionUri);
        }

        if (this.nonOwnedNeedUri) {
          this.connections__connectAdHoc(
            this.nonOwnedNeedUri,
            message,
            persona
          );
        }

        //this.router__stateGoCurrent({connectionUri: null, sendAdHocRequest: null});
      } else {
        this.connections__rate(this.connectionUri, won.WON.binaryRatingGood);
        this.needs__connect(
          this.ownedNeed.get("uri"),
          this.connectionUri,
          this.nonOwnedNeedUri,
          message
        );
        this.router__stateGoCurrent({ connectionUri: this.connectionUri });
      }
    }

    closeConnection(rateBad = false) {
      rateBad &&
        this.connections__rate(
          this.connection.get("uri"),
          won.WON.binaryRatingBad
        );
      this.connections__close(this.connection.get("uri"));
      this.router__stateGoCurrent({ connectionUri: null });
    }

    rateMatch(rating) {
      if (!this.isConnected) {
        return;
      }
      switch (rating) {
        case won.WON.binaryRatingGood:
          this.connections__rate(this.connectionUri, won.WON.binaryRatingGood);
          break;

        case won.WON.binaryRatingBad:
          this.connections__close(this.connectionUri);
          this.connections__rate(this.connectionUri, won.WON.binaryRatingBad);
          this.router__stateGoCurrent({ connectionUri: null });
          break;
      }
    }

    selectMessage(msg) {
      const selected = msg.getIn(["viewState", "isSelected"]);

      this.messages__viewState__markAsSelected({
        messageUri: msg.get("uri"),
        connectionUri: this.connection.get("uri"),
        needUri: this.ownedNeed.get("uri"),
        isSelected: !selected,
      });
    }
  }
  Controller.$inject = serviceDependencies;

  return {
    restrict: "E",
    controller: Controller,
    controllerAs: "self",
    bindToController: true, //scope-bindings -> ctrl
    scope: {},
    template: template,
  };
}

export default angular
  .module("won.owner.components.groupPostMessages", [
    autoresizingTextareaModule,
    chatTextFieldSimpleModule,
    connectionMessageModule,
    connectionHeaderModule,
    labelledHrModule,
    connectionContextDropdownModule,
    postContentMessageModule,
    petrinetStateModule,
  ])
  .directive("wonGroupPostMessages", genComponentConf).name;
