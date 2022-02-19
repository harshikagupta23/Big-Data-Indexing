package com.java.bigDataIndexing.controller;

import com.java.bigDataIndexing.service.PlanService;
import com.java.bigDataIndexing.service.ValidateJSON;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.ValidationException;
import java.net.URISyntaxException;

import java.net.URI;

@RestController
public class PlanController {

    PlanService planService = new PlanService();
    ValidateJSON jsonValidator = new ValidateJSON();
    @RequestMapping(method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE, value = "/plan")
    public ResponseEntity createPlan(@Valid @RequestBody String data) throws URISyntaxException {

        if (data == null || data.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error", "Please provide the JSON body, cannot accept null").toString());
        }
        JSONObject jsonPlan = new JSONObject(new JSONTokener(data));
        System.out.println("create plan .." + (String) jsonPlan.get("objectId"));
        try {
            jsonValidator.validateJSON(jsonPlan);
        } catch(ValidationException ex){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error",ex.getMessage()).toString());
        }
        if(this.planService.checkIfKeyExists((String) jsonPlan.get("objectId"))){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new JSONObject().put("message", "Plan already exists").toString());
        }

        String objectKey = this.planService.savePlan(jsonPlan);
        JSONObject response = new JSONObject();
        response.put("objectId", objectKey);
        response.put("message", "Plan Created Successfully!");

        return ResponseEntity.created(new URI("/plan/" +jsonPlan.get("objectId").toString())).body(response.toString());
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE, value = "/plan/{objectKey}")
    public ResponseEntity getPlan(@PathVariable String objectKey){
        if(!this.planService.checkIfKeyExists(objectKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "Object doesn't exist").toString());
        } else {
            JSONObject jsonObject = this.planService.getPlan(objectKey);
            return ResponseEntity.ok().body(jsonObject.toString());
        }
    }

    @RequestMapping(method =  RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE, value = "/plan/{objectKey}")
    public ResponseEntity deletePlan(@PathVariable String objectKey){

        if(!this.planService.checkIfKeyExists(objectKey)){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "Object doesn't exist").toString());
        }

        this.planService.deletePlan(objectKey);
        return ResponseEntity.noContent().build();
    }
}
