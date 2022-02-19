package com.java.bigDataIndexing.service;

import com.java.bigDataIndexing.controller.PlanController;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;


public class ValidateJSON {

    public void validateJSON(JSONObject jsonObject) throws ValidationException {
        JSONObject jsonSchema = new JSONObject(new JSONTokener(PlanController.class.getResourceAsStream("/planCostSchema.json")));
        Schema schema = SchemaLoader.load(jsonSchema);
        schema.validate(jsonObject);
    }
}
