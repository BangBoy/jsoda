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


/** Mark a method in a model class as PostLoad
 *
 * The PostLoad method will be called after a model object is loaded from database,
 * giving the app a chance to do post loading data setup, e.g. filling in the transient fields.
 * 
 * When a model class inherits from a base model class, its PostLoad method will be called
 * instead of the base class' one.  Call the base class' PostLoad method in the derived class one.
 * 
 * Note that cached object coming from cache won't call the PostLoad method.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface PostLoad {
}
