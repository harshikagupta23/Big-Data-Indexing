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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

@RestController
public class PlanController {

    PlanService planService = new PlanService();
    ValidateJSON jsonValidator = new ValidateJSON();

    public String getETag(JSONObject json) {

        String encoded=null;
        try {
            MessageDigest dig = MessageDigest.getInstance("SHA-256");
            byte[] hashedValue = dig.digest(json.toString().getBytes(StandardCharsets.UTF_8));
            encoded = Base64.getEncoder().encodeToString(hashedValue);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "\""+encoded+"\"";
    }

    public boolean verifyETag(JSONObject json, List<String> etags) {
        if(etags.isEmpty())
            return false;
        String encoded=getETag(json);
        return etags.contains(encoded);

    }

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

        String etag = this.getETag(jsonPlan);
        JSONObject response = new JSONObject();
        response.put("objectId", objectKey);
        response.put("message", "Plan Created Successfully!");

        return ResponseEntity.created(new URI("/plan/" +jsonPlan.get("objectId").toString())).eTag(etag).body(response.toString());
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE, value = "/plan/{objectKey}")
    public ResponseEntity getPlan(@PathVariable String objectKey, @RequestHeader HttpHeaders requestHeaders){
        if(!this.planService.checkIfKeyExists(objectKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("message", "Object doesn't exist").toString());
        } else {
            JSONObject jsonObject = this.planService.getPlan(objectKey);
            String etag = this.getETag(jsonObject);

            List<String> ifNotMatch;
            try{
                ifNotMatch = requestHeaders.getIfNoneMatch();
            } catch (Exception e){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).
                        body(new JSONObject().put("error", "ETag value is invalid! If-None-Match value should be string.").toString());
            }

            if(!this.verifyETag(jsonObject, ifNotMatch)){
                return ResponseEntity.ok().eTag(etag).body(jsonObject.toString());
            } else {
                return  ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
            }
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
