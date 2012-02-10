/******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0.  If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 * 
 * Software distributed under the License is distributed on an "AS IS" basis, 
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for 
 * the specific language governing rights and limitations under the License.
 *
 * The Original Code is: Jsoda
 * The Initial Developer of the Original Code is: William Wong (williamw520@gmail.com)
 * Portions created by William Wong are Copyright (C) 2012 William Wong, All Rights Reserved.
 *
 ******************************************************************************/


package wwutil.model.annotation;

import java.lang.annotation.*;


/**
 * Model class annotation specifying the cache policy.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CachePolicy {
    /** Objects of the model class are cached or not.
     * Cacheable class must also implement Serializable.
     * Default is to cache all Serializable classes automatically.
     * Set cacheable to false if the class should not be cached.
     */
    public boolean cacheable() default true;

    /** The cached object will expire in the number of seconds.  The default value (0) means no expiration.
     * Note that expired object causes a reload from db on the next get(), not removal of the cached object.
     * Cached objects are removed as a policy of capacity restriction.
     */
    public int expireInSeconds() default 0;
}
