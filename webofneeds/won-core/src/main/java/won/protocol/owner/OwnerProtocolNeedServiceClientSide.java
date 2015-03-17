/*
 * Copyright 2012  Research Studios Austria Forschungsges.m.b.H.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package won.protocol.owner;

import org.springframework.context.ApplicationContextAware;
import won.protocol.message.WonMessage;

import java.net.URI;

/**
 * User: LEIH-NB
 * Date: 17.10.13
 */
public interface OwnerProtocolNeedServiceClientSide extends ApplicationContextAware{

    /**
     * registers the owner application on WON Node and receive client ID
     *
     * @param endpointURI
     * */
    // ToDo (FS): this one shouldn't be here, right?
     public String register(URI endpointURI) throws Exception;

    public void sendWonMessage(WonMessage wonMessage) throws Exception;

}
