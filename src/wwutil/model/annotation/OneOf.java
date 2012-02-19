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


/** Validate a String field equals to one of the choices.
 *
 *<pre> 
 * e.g. @OneOf( choices = {"Red", "Green", "Blue"} )
 * will match "(415) 555-1212" or "(408) 121-5555"
 *
 *</pre> 
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface OneOf {
    public String[] choices();
}

