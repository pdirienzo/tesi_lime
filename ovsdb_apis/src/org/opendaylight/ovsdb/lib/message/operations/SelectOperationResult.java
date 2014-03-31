/*
 * Copyright (C) 2013 Red Hat, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Authors : Madhu Venugopal
 */
package org.opendaylight.ovsdb.lib.message.operations;


import java.util.HashMap;
import java.util.List;

import org.opendaylight.ovsdb.lib.notation.OvsdbRow;
import org.opendaylight.ovsdb.lib.notation.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

// Section 5.2 of ovsdb draft covers the various response structures for
// each of the Operations covered by Transaction (Insert, Update, Delete, Mutate, etc...)
// It is better to have the OperationResult as an abstract parent class with individual
// concrete child classes for each of the operation response.
// TODO : But this needs proper response handling
// https://trello.com/c/mfTTS86k/28-generic-response-error-handling-especially-for-transact
// As a temporary measure, adding all the expected responses under the same response.

public class SelectOperationResult {
    //public abstract boolean isSuccess();
    private int count;
    @JsonIgnore
    private UUID uuid;
    //private List<HashMap<String,Object>> rows;
    private List<OvsdbRow> rows;
    private String error;
    private String details;
    private String syntax;

    public int getCount() {
        return count;
    }
    public void setCount(int count) {
        this.count = count;
    }
    @JsonProperty("uuid")
    public UUID getUuid() {
        return uuid;
    }
    
    public void setSyntax(String syntax){
    	this.syntax = syntax;
    }
    
    public String getSyntax(){
    	return syntax;
    }
    
    public void setUuid(List<String> uuidList) {
        this.uuid = new UUID(uuidList.get(1));
    }
    /*
    public List<HashMap<String,Object>> getRows() {
        return rows;
    }
    
    public void setRows(List<HashMap<String,Object>> rows) {
        this.rows = rows;
    }
    */
    public List<OvsdbRow> getRows(){
    	return rows;
    }
    
    public void setRows(List<OvsdbRow> rows) {
        this.rows = rows;
    }
    
    public String getError() {
        return error;
    }
    public void setError(String error) {
        this.error = error;
    }
    public String getDetails() {
        return details;
    }
    public void setDetails(String details) {
        this.details = details;
    }

}
