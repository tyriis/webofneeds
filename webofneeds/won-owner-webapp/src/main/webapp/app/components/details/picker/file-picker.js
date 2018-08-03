import angular from "angular";
import { attach, delay } from "../../../utils.js";
import { DomCache } from "../../../cstm-ng-utils.js";
import dropzoneModule from "../../file-dropzone.js";

const serviceDependencies = ["$scope", "$element"];
function genComponentConf() {
  let template = `
      <won-file-dropzone on-image-picked="::self.updateFiles(image)" accepts="::self.detail.accepts">
      </won-file-dropzone>
      <div class="filep__header" ng-if="self.addedFiles && self.addedFiles.length > 0">
        Chosen Files:
      </div>
      <div class="filep__preview" ng-if="self.addedFiles && self.addedFiles.length > 0">
        <div class="filep__preview__item"
          ng-repeat="file in self.addedFiles">
          <div class="filep__preview__item__label">
            {{ file.name }}
          </div>
          <svg
            class="filep__preview__item__remove"
            ng-click="self.removeFile(file)">
            <use xlink:href="#ico36_close" href="#ico36_close"></use>
          </svg>
          <img class="filep__preview__item__image"
            ng-if="self.isImage(file)"
            alt="{{file.name}}"
            ng-src="data:{{file.type}};base64,{{file.data}}"/>
          <svg ng-if="!self.isImage(file)"
            class="filep__preview__item__typeicon">
            <use xlink:href="#ico36_uc_transport_demand" href="#ico36_uc_transport_demand"></use>
          </svg>
        </div>
      </div>
    `;

  class Controller {
    constructor() {
      attach(this, serviceDependencies, arguments);
      this.domCache = new DomCache(this.$element);

      window.filep4dbg = this;

      this.addedFiles = this.initialValue;

      delay(0).then(() => this.showInitialFiles());
    }

    /**
     * Checks validity and uses callback method
     */
    update(data) {
      if (data) {
        this.onUpdate({ value: data });
      } else {
        this.onUpdate({ value: undefined });
      }
    }

    showInitialFiles() {
      this.addedFiles = this.initialValue;
      this.$scope.$apply();
    }

    updateFiles(file) {
      console.log("called updateFiles: ", file);
      if (!this.addedFiles) {
        this.addedFiles = [];
      }
      this.addedFiles.push(file);
      this.update(this.addedFiles);
      this.$scope.$apply();
    }

    removeFile(file) {
      console.log("Removing file: " + file);
      //TODO: IMPL ME
    }

    isImage(file) {
      return file && /^image\//.test(file.type);
    }
  }
  Controller.$inject = serviceDependencies;

  return {
    restrict: "E",
    controller: Controller,
    controllerAs: "self",
    bindToController: true, //scope-bindings -> ctrl
    scope: {
      onUpdate: "&",
      initialValue: "=",
      detail: "=",
    },
    template: template,
  };
}

export default angular
  .module("won.owner.components.filePicker", [dropzoneModule])
  .directive("wonFilePicker", genComponentConf).name;
