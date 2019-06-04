/**
 * Created by quasarchimaere on 19.11.2018.
 */
import angular from "angular";
import { attach, getIn } from "../utils.js";
import { actionCreators } from "../actions/actions.js";
import { connect2Redux } from "../won-utils.js";

import "~/style/_menu.scss";

function genTopnavConf() {
  let template = `
    <div class="menu">
      <a class="menu__tab" ng-click="self.router__stateGo('inventory')" ng-class="{'menu__tab--selected': self.showInventory}">Inventory</a>
      <a class="menu__tab" ng-click="self.router__stateGo('connections')" ng-class="{'menu__tab--selected': self.showChats}">Chats</a>
      <a class="menu__tab" ng-click="self.router__stateGo('create')" ng-class="{'menu__tab--selected': self.showCreate}">Create</a>
      <a class="menu__tab menu__tab--selected" ng-if="self.showWhatsNew">What's New</a>
      <a class="menu__tab menu__tab--selected" ng-if="self.showWhatsAround">What's Around</a>
      <a class="menu__tab menu__tab--selected" ng-if="self.showSignUp">Sign Up</a>
    </div>
  `;

  const serviceDependencies = [
    "$ngRedux",
    "$scope",
    "$state" /*injections as strings here*/,
  ];

  class Controller {
    constructor(/* arguments <- serviceDependencies */) {
      attach(this, serviceDependencies, arguments);

      window.menu4dbg = this;

      const selectFromState = state => {
        const currentRoute = getIn(state, ["router", "currentState", "name"]);

        return {
          showInventory: currentRoute === "inventory",
          showChats: currentRoute === "connections",
          showWhatsNew: currentRoute === "overview",
          showWhatsAround: currentRoute === "map",
          showSignUp: currentRoute === "signup",
          showCreate: currentRoute === "create",
        };
      };

      connect2Redux(selectFromState, actionCreators, [], this);
    }
  }
  Controller.$inject = serviceDependencies;

  return {
    restrict: "E",
    scope: {}, //isolate scope to allow usage within other controllers/components
    controller: Controller,
    controllerAs: "self",
    bindToController: true, //scope-bindings -> ctrl
    template: template,
  };
}

export default angular
  .module("won.owner.components.menu", [])
  .directive("wonMenu", genTopnavConf).name;
