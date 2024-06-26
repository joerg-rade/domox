/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package generate

import domox.dom.uml.AssociationCdd
import domox.dom.uml.ClassCdd
import domox.dom.uml.DomainModel

class ClassDiagram {

    fun build(dm: DomainModel): String {
        val code = PumlCode()
        dm.classList.forEach { cls: ClassCdd ->
            amendWithClass(code, cls)
        }
        dm.classList.forEach { cls: ClassCdd ->
            amendWithAssociation(code, cls)
        }
        code.asUml()
        return code.code
    }

    private fun amendWithAssociation(code: PumlCode, cls: ClassCdd): PumlCode {
        cls.associationList.forEach { asc: AssociationCdd ->
            code.addAssociation(asc.toPlantUmlString())
        }
        return code
    }

    private fun amendWithClass(code: PumlCode, cls: ClassCdd): PumlCode {
        return code.add(cls.toPlantUmlString());
    }

}
