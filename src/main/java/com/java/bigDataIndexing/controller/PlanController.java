package com.java.bigDataIndexing.controller;

import com.java.bigDataIndexing.service.AuthorizeService;
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
import javax.ws.rs.BadRequestException;
import java.io.IOException;
import java.net.URISyntaxException;

import java.net.URI;
import java.util.Map;

@RestController
public class PlanController {

    PlanService planService;
    AuthorizeService authorizeService;
    ValidateJSON validateJSON;

    public PlanController(PlanService planService, AuthorizeService authorizeService, ValidateJSON validateJSON) {

        this.planService = planService;
        this.authorizeService = authorizeService;
        this.validateJSON = validateJSON;

    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE, value = "/token")
    public ResponseEntity getToken(){

        String token;
        try {
            token = authorizeService.generateToken();
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(new JSONObject().put("token", token).toString());

    }

    @RequestMapping(method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE, value = "/plan")
    public ResponseEntity createPlan(@Valid @RequestBody(required = false) String data,
                                     @RequestHeader HttpHeaders requestHeaders) throws URISyntaxException {

        String authorization = requestHeaders.getFirst("Authorization");
        String result = authorizeService.authorize(authorization);
        if(result != "Valid Token"){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Error: ", result).toString());
        }
        if (data == null || data.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error", "Please provide the JSON body, cannot accept null").toString());
        }
        JSONObject jsonPlan = new JSONObject(new JSONTokener(data));
        try {
            validateJSON.validateJSON(jsonPlan);
        } catch(ValidationException ex){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error",ex.getMessage()).toString());
        }
        if(this.planService.checkIfKeyExists((String) jsonPlan.get("objectId"))){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new JSONObject().put("message", "Plan already exists").toString());
        }
        System.out.println("create " + jsonPlan.get("objectType") + ":" + jsonPlan.get("objectId"));
        String objectKey = jsonPlan.get("objectType") + ":" + jsonPlan.get("objectId");
        String etag = this.planService.savePlan(jsonPlan, objectKey);

//        String etag = this.getETag(jsonPlan);
        JSONObject response = new JSONObject();
        response.put("objectId", objectKey);
        response.put("message", "Plan Created Successfully!");

        return ResponseEntity.created(new URI("/plan/" +jsonPlan.get("objectId").toString())).eTag(etag).body(response.toString());
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE, value = "/{objectType}/{objectID}")
    public ResponseEntity getPlan(@PathVariable String objectID, @PathVariable String objectType, @RequestHeader HttpHeaders requestHeaders){

        String authorization = requestHeaders.getFirst("Authorization");
        String result = authorizeService.authorize(authorization);
        if(result != "Valid Token") {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Error: ", result).toString());
        }
        System.out.println("get " + objectType + ":" + objectID);
        String objectKey = objectType + ":" + objectID;
        if(!this.planService.checkIfKeyExists(objectKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "Object doesn't exist").toString());
        } else {
            String ifNotMatch;
            try{
                ifNotMatch = requestHeaders.getFirst("If-None-Match");
            } catch (Exception e){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                        body(new JSONObject().put("error", "ETag value is invalid! If-None-Match value should be string.").toString());
            }
            if(objectType.equals("plan")){
                String actualEtag = this.planService.getEtag(objectKey);
                if (ifNotMatch != null && ifNotMatch.equals(actualEtag)) {
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(actualEtag).build();
                }
            }
            Map<String, Object> plan = this.planService.getPlan(objectKey);

            if (objectType.equals("plan")) {
                String actualEtag = this.planService.getEtag(objectKey);
                return ResponseEntity.ok().eTag(actualEtag).body(new JSONObject(plan).toString());
            }

            return ResponseEntity.ok().body(new JSONObject(plan).toString());
        }
    }
    @RequestMapping(method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE, path = "/plan/{objectID}")
    public ResponseEntity updatePlan( @RequestHeader HttpHeaders requestHeaders, @Valid @RequestBody(required = false) String jsonData,
                                      @PathVariable String objectID) throws IOException {

        String authorization = requestHeaders.getFirst("Authorization");
        String result = authorizeService.authorize(authorization);
        if(result != "Valid Token"){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Error: ", result).toString());
        }

        if (jsonData == null || jsonData.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error", "Request body is Empty. Kindly provide the JSON").toString());
        }

        JSONObject jsonPlan = new JSONObject(jsonData);
        String key = "plan:" + objectID;

        if(!this.planService.checkIfKeyExists(key)){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "ObjectId does not exists!!").toString());
        }

        String actualEtag = planService.getEtag(key);
        String eTag = requestHeaders.getFirst("If-Match");
        if (eTag == null || eTag.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("message", "eTag not provided in request!!").toString());
        }
        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag)
                    .body(new JSONObject().put("message", "Plan has been updated by another user!!").toString());
        }

        try {
            validateJSON.validateJSON(jsonPlan);
        } catch(ValidationException ex){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error",ex.getMessage()).toString());
        }

        this.planService.deletePlan(key);
        String newEtag = this.planService.savePlan(jsonPlan, key);

        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject().put("message: ", "Resource updated successfully!!").toString());
    }

    @RequestMapping(method =  RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE, value = "/{objectType}/{objectID}")
    public ResponseEntity deletePlan(@RequestHeader HttpHeaders requestHeaders, @PathVariable String objectID,  @PathVariable String objectType){

        String authorization = requestHeaders.getFirst("Authorization");
        String result = authorizeService.authorize(authorization);
        if(result != "Valid Token"){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Error: ", result).toString());
        }

        String objectKey = objectType + ":" + objectID;
        if(!this.planService.checkIfKeyExists(objectKey)){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "Object id doesn't exist").toString());
        }

        String actualEtag = planService.getEtag(objectKey);
        String eTag = requestHeaders.getFirst("If-Match");
        if (eTag == null || eTag.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("message", "eTag not provided in request!!").toString());
        }
        if (eTag != null && !eTag.equals(actualEtag)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag)
                    .body(new JSONObject().put("message", "Plan has been updated by another user!!").toString());
        }

        this.planService.deletePlan(objectKey);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(method =  RequestMethod.PATCH, produces = MediaType.APPLICATION_JSON_VALUE, path = "/plan/{objectID}")
    public ResponseEntity<Object> patchPlan(@RequestHeader HttpHeaders requestHeaders, @Valid @RequestBody(required = false) String jsonData,
                                            @PathVariable String objectID) throws IOException {

        String authorization = requestHeaders.getFirst("Authorization");
        String result = authorizeService.authorize(authorization);
        if(result != "Valid Token"){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JSONObject().put("Error: ", result).toString());
        }

        if (jsonData == null || jsonData.isEmpty()){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                    body(new JSONObject().put("error", "Request body is Empty. Kindly provide the JSON").toString());
        }

        JSONObject jsonPlan = new JSONObject(jsonData);
        String key = "plan:" + objectID;
        if (!planService.checkIfKeyExists(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "ObjectId does not exists!!").toString());
        }

        String actualEtag = planService.getEtag(key);
        String eTag = requestHeaders.getFirst("If-Match");
        if (eTag == null || eTag.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("message", "eTag not provided in request!!").toString());
        }
        if (eTag != null && !eTag.equals(actualEtag)) {
            System.out.println(actualEtag + "actual tag");
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(actualEtag)
                    .body(new JSONObject().put("message", "Plan has been updated by another user!!").toString());
        }

        String newEtag =  this.planService.savePlan(jsonPlan, key);

        return ResponseEntity.ok().eTag(newEtag)
                .body(new JSONObject().put("message: ", "Resource updated successfully!!").toString());
    }
}
