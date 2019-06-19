package com.espresoh;

import com.fullcontact.api.libs.fullcontact4j.FullContact;
import com.fullcontact.api.libs.fullcontact4j.FullContactException;
import com.fullcontact.api.libs.fullcontact4j.enums.Casing;
import com.fullcontact.api.libs.fullcontact4j.http.location.LocationEnrichmentRequest;
import com.fullcontact.api.libs.fullcontact4j.http.location.LocationEnrichmentResponse;
import com.fullcontact.api.libs.fullcontact4j.http.name.NameDeduceRequest;
import com.fullcontact.api.libs.fullcontact4j.http.name.NameResponse;
import com.fullcontact.api.libs.fullcontact4j.http.person.PersonRequest;
import com.fullcontact.api.libs.fullcontact4j.http.person.PersonResponse;
import com.fullcontact.api.libs.fullcontact4j.http.person.model.SocialProfile;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App {

    private static FullContact client;
    private static final Logger logger = Logger.getLogger(App.class.getName());

    //the first argument should be a business email with somebody's name (like bart@fullcontact.com!)
    public static void main(String[] args) {
        String fullcontactAPIKey = "kQmvLPkiKduEUUp5FjrZoyE0YrXA6zmB";
        client = FullContact.withApiKey(fullcontactAPIKey).build();

        if(args.length == 0) {
            logger.log(Level.INFO, "Please include a business email as the first parameter.");
            return;
        }
        String email = args[0];
        logger.log(Level.INFO, email);

        getNameFromEmail(email);
        String potentialLocation = lookupTwitterAndLocation(email);
        lookupPopulation(potentialLocation);

        client.shutdown();
    }

    /**
     * Given an email, let's use FullContact to get a full name
     * (This only works with emails with the name inside, like john.smith@business.com)
     */
    private static void getNameFromEmail(String email) {

        // First, let's build a request to the name deducer. It takes an email or a username parameter...
        // we have email, and want it formatted TitleCase (fancy!)
        NameDeduceRequest nameFromEmailRequest = client.buildNameDeduceRequest().email(email).casing(Casing.TITLECASE)
                .build();

        try {
            //actually send the request. this method will return when the request comes back
            NameResponse response = client.sendRequest(nameFromEmailRequest);

            // Get the full name from the response object
            String name = response.getNameDetails().getFullName();
            logger.log(Level.INFO, "That person's name is probably {0}!", name);
        } catch(FullContactException e) {

            // We got a response code that wasn't in the 200s from FullContact...let's see why
            // (check out the API documentation for the potential error codes)
            Integer errorCode = e.getErrorCode();

            if(errorCode == 422 || errorCode == 400 || errorCode == 404) {
                logger.log(Level.INFO, "Error: Your email was not a valid format or didn't contain a name.");
            } else {
                logger.log(Level.INFO, "Error: Some unknown error occurred. Here's the message:");
                logger.log(Level.INFO, e.getMessage());
            }
        }
    }

    /**
     * Given an email, let's use FullContact to try to find an associated Twitter account and location.
     */
    private static String lookupTwitterAndLocation(String email) {

        PersonRequest personLookupRequest = client.buildPersonRequest().email(email).build();

        try {
            PersonResponse response = client.sendRequest(personLookupRequest);

            if(response.getStatus() == 200) {
                // 200- success! Let's get some info from the response
                SocialProfile twitter = response.getSocialProfile("twitter");
                if(twitter != null) {
                    logger.log(Level.INFO, MessageFormat.format("Their twitter handle is probably {0} and they have {1} followers!", twitter.getUsername(), twitter.getFollowers()));
                } else {
                    logger.log(Level.INFO, "This person doesn't seem to have a twitter.");
                }
                return response.getDemographics().getLocationGeneral();
            }

            if(response.getStatus() == 202) {
                // 202 - FullContact's "we are searching for this person" response. We can try again later
                logger.log(Level.INFO, "FullContact is searching for this person. Re-run this again in a few minutes.");
            }

        } catch(FullContactException e) {

            if(e.getErrorCode() == 404) {
                logger.log(Level.INFO, "Can't find this person. Sorry :(");
            } else {
                logger.log(Level.INFO, "Unknown Error: " + e.getMessage());
            }
        }
        return "";
    }

    private static void lookupPopulation(String location) {
        if(location.isEmpty()) {
            logger.log(Level.INFO, "This person did not have a location in their profile...");
            return;
        }
        LocationEnrichmentRequest locationRequest = client.buildLocationEnrichmentRequest(location).build();

        try {
            LocationEnrichmentResponse locationResponse = client.sendRequest(locationRequest);

            if(locationResponse.getPossibleLocations().isEmpty()) {
                logger.log(Level.INFO, location + " didn't seem to correlate to a place.");
                return;
            }

            logger.log(Level.INFO, "This location has a population of " +
                    locationResponse.getPossibleLocations().get(0).getPopulation());

        } catch(FullContactException e) {
            logger.log(Level.INFO, "Error Occurred: " + e.getMessage());
        }
    }
}
