/**
 * Created by quasarchimaere on 30.07.2019.
 */
import React from "react";
import {get, getIn} from "../utils.js";
import {actionCreators} from "../actions/actions.js";
import * as atomUtils from "../redux/utils/atom-utils";
import * as generalSelectors from "../redux/selectors/general-selectors";
import * as connectionSelectors from "../redux/selectors/connection-selectors";
import * as connectionUtils from "../redux/utils/connection-utils";
import won from "../won-es6";
import WonLabelledHr from "./labelled-hr.jsx";
import WonSuggestAtomPicker from "./details/picker/suggest-atom-picker.jsx";
import WonAtomCard from "./atom-card.jsx";

import "~/style/_atom-content-participants.scss";
import VisibilitySensor from "react-visibility-sensor";

export default class WonAtomContentBuddies extends React.Component {
  constructor(props){
    super(props);
    this.localState = {
      suggestAtomExpanded: false
    };
  }


  componentDidMount() {
    this.atomUri = this.props.atomUri;
    this.disconnect = this.props.ngRedux.connect(
      this.selectFromState.bind(this),
      actionCreators
    )(state => {
      this.setState({...state, ...this.localState});
    });
  }

  componentWillUnmount() {
    this.disconnect();
  }

  UNSAFE_componentWillReceiveProps(nextProps) {
    this.atomUri = nextProps.atomUri;
    this.setState({...this.selectFromState(this.props.ngRedux.getState()), ...this.localState});
  }

  toggleSuggestions() {
    this.localState.suggestAtomExpanded = !this.localState.suggestAtomExpanded;
    this.setState({...this.state, ...this.localState});
  }

  selectFromState(state) {
    const atom = getIn(state, ["atoms", this.atomUri]);
    const isOwned = generalSelectors.isAtomOwned(state, this.atomUri);

    const hasBuddySocket = atomUtils.hasBuddySocket(atom);

    const buddies = hasBuddySocket && get(atom, "buddies");

    const buddyConnections =
      isOwned &&
      hasBuddySocket &&
      connectionSelectors.getBuddyConnectionsByAtomUri(
        state,
        this.atomUri,
        true,
        true
      );

    let excludedFromRequestUris = [this.atomUri];

    if (buddyConnections) {
      buddyConnections.map(conn =>
        excludedFromRequestUris.push(get(conn, "targetAtomUri"))
      );
    }

    return {
      atom,
      isOwned,
      hasBuddySocket,
      hasBuddies: buddies && buddies.size > 0,
      hasBuddyConnections: buddyConnections && buddyConnections.size > 0,
      buddyConnectionsArray: buddyConnections && buddyConnections.toArray(),
      excludedFromRequestUris,
      buddiesArray: buddies && buddies.toArray(),
    };
  }

  render() {
    //TODO
    if (!this.state) {
      console.debug("render with null state");
      return <div/>;
    }

    let participants;

    if (this.state.isOwned) {
      if (this.state.hasBuddyConnections) {
        participants = this.state.buddyConnectionsArray.map(conn => {
          if(!connectionUtils.isClosed(conn)) {
            let actionButtons;

            if(connectionUtils.isRequestReceived(conn)) {
              actionButtons = (
                <div className="acb__buddy__actions">
                  <div
                    className="acb__buddy__actions__button red won-button--outlined thin"
                    onClick={() => this.openRequest(conn)}>
                    Accept
                  </div>
                  <div
                    className="acb__buddy__actions__button red won-button--outlined thin"
                    onClick={() => this.closeConnection(conn)}>
                    Reject
                  </div>
                </div>
              );
            } else if (connectionUtils.isSuggested(conn)) {
              actionButtons = (
                <div className="acb__buddy__actions">
                  <div
                    className="acb__buddy__actions__button red won-button--outlined thin"
                    onClick={() => this.requestBuddy(conn)}>
                    Request
                  </div>
                  <div
                    className="acb__buddy__actions__button red won-button--outlined thin"
                    onClick={() => this.closeConnection(conn)}>
                    Remove
                  </div>
                </div>
              )
            } else if (connectionUtils.isRequestSent(conn)) {
              actionButtons = (
                <div className="acb__buddy__actions">
                  <div className="acb__buddy__actions__button red won-button--outlined thin" disabled={true}>
                    Waiting for Accept...
                  </div>
                </div>
              );
            } else if (connectionUtils.isConnected(conn)) {
              actionButtons = (
                <div className="acb__buddy__actions">
                  <div
                    className="acb__buddy__actions__button red won-button--outlined thin"
                    onClick={() => this.closeConnection(conn)}>
                    Remove
                  </div>
                </div>
              );
            } else {
              actionButtons = (
                <div className="acb__buddy__actions"/>
              );
            }

            return (
              <VisibilitySensor key={get(conn, "uri")} onChange={(isVisible) => { isVisible && connectionUtils.isUnread(conn) && this.markAsRead(conn) }} intervalDelay={2000}>
                <div className={"acb__buddy " + (connectionUtils.isUnread(conn) ? " won-unread " : "")}>
                  <WonAtomCard atomUri={get(conn, 'targetAtomUri')} currentLocation={this.state.currentLocation} showSuggestions={false} showPersona={false} ngRedux={this.props.ngRedux}/>
                  {actionButtons}
                </div>
              </VisibilitySensor>
            );
          }
        });
      } else {
        participants = (
          <div className="acp__empty">No Buddies present.</div>
        );
      }

      return (
        <won-atom-content-participants>
          {participants}
          <WonLabelledHr label="Request" arrow={this.state.suggestAtomExpanded? "up" : "down"} onClick={() => this.toggleSuggestions()}/>
          {
            this.state.suggestAtomExpanded
            ? (
              <WonSuggestAtomPicker
                initialValue={undefined}
                onUpdate={this.requestBuddy}
                detail={{placeholder: "Insert AtomUri to invite"}}
                excludedUris={this.state.excludedFromRequestUris}
                allowedSockets={[won.BUDDY.BuddySocketCompacted]}
                excludedText="Requesting yourself or someone who is already your Buddy is not allowed"
                notAllowedSocketText="Request does not work on atoms without the Buddy Socket"
                noSuggestionsText="No known Personas available"
                ngRedux={this.props.ngRedux}
              />
            )
            : undefined
          }
        </won-atom-content-participants>
      );
    } else {
      if (this.state.hasBuddies) {
        participants = this.state.buddiesArray.map(memberUri => {
          return (
            <div className="acb__buddy" key={memberUri}>
              <WonAtomCard atomUri={memberUri} currentLocation={this.state.currentLocation} showSuggestions={false} showPersona={false} ngRedux={this.props.ngRedux}/>
              <div className="acb__buddy__actions"></div>
            </div>
          );
        });
      } else {
        participants = (
          <div className="acp__empty">No Buddies present.</div>
        );
      }

      return (
        <won-atom-content-participants>
          {participants}
        </won-atom-content-participants>
      );
    }
  }

  closeConnection(conn, rateBad = false) {
    if (!conn) {
      return;
    }

    const payload = {
      caption: "Persona",
      text: "Remove Buddy?",
      buttons: [
        {
          caption: "Yes",
          callback: () => {
            const connUri = get(conn, "uri");

            if (connectionUtils.isUnread(conn)) {
              this.props.ngRedux.dispatch(actionCreators.connections__markAsRead({
                connectionUri: connUri,
                atomUri: this.atomUri,
              }));
            }

            this.props.ngRedux.dispatch(actionCreators.connections__close(connUri));
            this.props.ngRedux.dispatch(actionCreators.view__hideModalDialog());
          },
        },
        {
          caption: "No",
          callback: () => {
            this.props.ngRedux.dispatch(actionCreators.view__hideModalDialog());
          },
        },
      ],
    };
    this.props.ngRedux.dispatch(actionCreators.view__showModalDialog(payload));
  }

  rejectConnection(conn) {
    if (!conn) {
      return;
    }

    const payload = {
      caption: "Persona",
      text: "Reject Buddy Request?",
      buttons: [
        {
          caption: "Yes",
          callback: () => {
            const connUri = get(conn, "uri");

            if (connectionUtils.isUnread(conn)) {
              this.props.ngRedux.dispatch(actionCreators.connections__markAsRead({
                connectionUri: connUri,
                atomUri: this.atomUri,
              }));
            }

            this.props.ngRedux.dispatch(actionCreators.connections__close(connUri));
            this.props.ngRedux.dispatch(actionCreators.view__hideModalDialog());
          },
        },
        {
          caption: "No",
          callback: () => {
            this.props.ngRedux.dispatch(actionCreators.view__hideModalDialog());
          },
        },
      ],
    };
    this.props.ngRedux.dispatch(actionCreators.view__showModalDialog(payload));
  }

  openRequest(conn, message = "") {
    if (!conn) {
      return;
    }

    const connUri = get(conn, "uri");

    if (connectionUtils.isUnread(conn)) {
      this.props.ngRedux.dispatch(actionCreators.connections__markAsRead({
        connectionUri: connUri,
        atomUri: this.atomUri,
      }));
    }

    this.props.ngRedux.dispatch(actionCreators.connections__open(connUri, message));
  }

  requestBuddy(targetAtomUri, message = "") {
    if (!this.isOwned || !this.hasBuddySocket) {
      console.warn("Trying to request a non-owned or non buddySocket atom");
      return;
    }

    const payload = {
      caption: "Persona",
      text: "Send Buddy Request?",
      buttons: [
        {
          caption: "Yes",
          callback: () => {
            this.props.ngRedux.dispatch(actionCreators.atoms__connect(
              this.atomUri,
              undefined,
              targetAtomUri,
              message,
              won.BUDDY.BuddySocketCompacted,
              won.BUDDY.BuddySocketCompacted
            ));
            this.props.ngRedux.dispatch(actionCreators.view__hideModalDialog());
          },
        },
        {
          caption: "No",
          callback: () => {
            this.props.ngRedux.dispatch(actionCreators.view__hideModalDialog());
          },
        },
      ],
    };
    this.props.ngRedux.dispatch(actionCreators.view__showModalDialog(payload));
  }

  sendRequest(conn, message = "") {
    //TODO
    if (!conn) {
      return;
    }

    const payload = {
      caption: "Group",
      text: "Add as Participant?",
      buttons: [
        {
          caption: "Yes",
          callback: () => {
            const connUri = get(conn, "uri");
            const targetAtomUri = get(conn, "targetAtomUri");

            if (connectionUtils.isUnread(conn)) {
              this.props.ngRedux.dispatch(actionCreators.connections__markAsRead({
                connectionUri: connUri,
                atomUri: this.atomUri,
              }));
            }

            this.props.ngRedux.dispatch(actionCreators.connections__rate(connUri, won.WONCON.binaryRatingGood));
            this.props.ngRedux.dispatch(actionCreators.atoms__connect(
              this.atomUri,
              connUri,
              targetAtomUri,
              message
            ));
            this.props.ngRedux.dispatch(actionCreators.view__hideModalDialog());
          },
        },
        {
          caption: "No",
          callback: () => {
            this.props.ngRedux.dispatch(actionCreators.view__hideModalDialog());
          },
        },
      ],
    };
    this.props.ngRedux.dispatch(actionCreators.view__showModalDialog(payload));
  }

  inviteParticipant(atomUri, message = "") {
    //TODO
    if (!this.state.isOwned || !this.state.hasGroupSocket) {
      console.warn("Trying to invite to a non-owned or non groupSocket atom");
      return;
    }
    this.props.ngRedux.dispatch(actionCreators.atoms__connect(this.atomUri, undefined, atomUri, message));
  }

  markAsRead(conn) {
    if (connectionUtils.isUnread(conn)) {
      this.props.ngRedux.dispatch(actionCreators.connections__markAsRead({
        connectionUri: get(conn, "uri"),
        atomUri: this.atomUri,
      }));
    }
  }
}