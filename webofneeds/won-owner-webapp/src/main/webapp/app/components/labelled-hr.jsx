/**
 * Created by fsuda on 21.08.2017.
 */

import React from "react";

import "~/style/_labelledhr.scss";

export default class WonLabelledHr extends React.Component {
  render() {
    return (
      <won-labelled-hr>
        <div className="wlh__label">
          <span className="wlh__label__text">{this.props.label}</span>
          {
            this.props.arrow
              ? (
                <svg className="wlh__label__carret clickable" >
                  <use xlinkHref={(this.props.arrow == 'down') ? "#ico16_arrow_down" : "#ico16_arrow_up"} href={(this.props.arrow == 'down') ? "#ico16_arrow_down" : "#ico16_arrow_up"}/>
                </svg>
              )
              : undefined
          }
        </div>
      </won-labelled-hr>
    );
  }
}
