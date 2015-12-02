/**
 * Created by ksinger on 24.09.2015.
 */

import { actionTypes } from '../actions/actions';
import { repeatVar } from '../utils';
import Immutable from 'immutable';
import { createReducer } from 'redux-immutablejs'
import { combineReducersStable } from '../redux-utils';
import draftsReducer from './drafts-reducer';
import { enqueuedMessagesReducer, sentMessagesReducer, receivedMessagesReducer } from './message-reducers'


/*
 * this reducer attaches a 'router' object to our state that keeps the routing state.
 */
import { router } from 'redux-ui-router';

const reducers = {
    router,

    wubs: createReducer(Immutable.List(), {
        [actionTypes.moreWub]: (state, action) => {
            const howMuch = action.payload;
            const additionalWubs = Immutable.fromJS(repeatVar('wub', howMuch));
            return state.concat(additionalWubs);
        }
    }),

    drafts: draftsReducer,
    user: createReducer(
        //initial state
        Immutable.Map(),

        //handlers
        {
            [actionTypes.user.receive]: (state, {payload: {loggedIn, email}}) => {
                if(loggedIn == true){
                    console.log('reducers.js: received successful-login action from app-server');
                    return Immutable.fromJS({loggedIn: true, email: email});
                }else{
                    console.log('reducers.js: received UNsuccessful-login action from app-server');
                    return Immutable.fromJS({loggedIn: false});
                }
            }
        }
    ),
    enqueuedMessages: enqueuedMessagesReducer,
    sentMessages: sentMessagesReducer,
    receivedMessages: receivedMessagesReducer,
}

/* note that `combineReducers` is opinionated as a root reducer for the
 * sake of convenience and ease of first use. It takes an object
 * with seperate reducers and applies each to it's seperate part of the
 * store/model. e.g.: an reducers object `{ drafts: function(state = [], action){...} }`
 * would result in a store like `{ drafts: [...] }`
 */
export default combineReducersStable(Immutable.Map(reducers));

window.ImmutableFoo = Immutable;



