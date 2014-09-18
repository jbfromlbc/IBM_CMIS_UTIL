/**
 * *******************************************************************
 * An Example OpenCMIS CMIS Server Extension that uses the IBM FileNet
 * System Manager Listener Java API for monitoring.
 * *******************************************************************
 * 
 * For more information about OpenCmis Server Extensions
 * Please see section 2 of the OpenCMIS Server Development Guide 
 * pdf link here:
 * https://github.com/cmisdocs/ServerDevelopmentGuide/blob/master/doc/OpenCMIS%20Server%20Development%20Guide.pdf?raw=true
 *  
 * For more info about the FileNet System Manager Listener API (PCH)
 * see :
 * http://www-01.ibm.com/support/knowledgecenter/SSNW2F_5.2.0/com.ibm.p8.sysmgr.dev.doc/sys_mgr_listen_java_api/p8plj012.htm
 * For more information about the System Dashboard for Enterprise Content Management 
 * see:
 * http://pic.dhe.ibm.com/infocenter/iconf/v5r2m0/index.jsp?topic=%2Fcom.ibm.p8.sysoverview.doc%2Fp8sov135.htm
 * 
 * 
 * Copyright 2014 Jay Brown, IBM
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is part of a training exercise and not intended for production use!
 *
 * 
 */
package com.ibm.ecm.cmis.extensions;

import java.math.BigInteger;

import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.data.ExtensionsData;
import org.apache.chemistry.opencmis.commons.data.ObjectInFolderList;
import org.apache.chemistry.opencmis.commons.data.ObjectList;
import org.apache.chemistry.opencmis.commons.enums.IncludeRelationships;
import org.apache.chemistry.opencmis.commons.server.CmisService;
import org.apache.chemistry.opencmis.server.support.wrapper.AbstractCmisServiceWrapper;

//
// IMPORTING PCH into your CMIS Server Extension
//
// You must use the listener.jar that you obtained from IBM.
//
// Assuming you are using maven:
// In order to import the PCH listener.jar like this you should first add the .jar to your
// maven repository like this:
//
// mvn install:install-file -Dfile=C:\listener.jar -DgroupId=com.filenet.pch -DartifactId=listener -Dversion=5.0.0.1 -Dpackaging=jar
//
// Then you can import pch in your pom like this:
//
// <dependency>
//    <groupId>com.filenet.pch</groupId>
//    <artifactId>listener</artifactId>
//    <version>5.0.0.1</version>
// </dependency>
//
//
import com.filenet.pch.Accumulator;
import com.filenet.pch.Container;
import com.filenet.pch.Duration;
import com.filenet.pch.Event;
import com.filenet.pch.Listener;
import com.filenet.pch.PCHeventClass;

public class BasicPchListener extends AbstractCmisServiceWrapper {

	// PCH objects (listener events and accumulators)
	protected static Listener listener = null;

	public static final String[] eventNames = { "allRequests", "query",
			"getChildren", "getContentStream" };
	public static final Event[] events = new Event[eventNames.length];
	public static final Accumulator[] durations = new Accumulator[eventNames.length];

	public static Container cmisContainer = null;
	public static final int ALL_REQUESTS_INDEX = 0;
	public static final int QUERY_EVENT_INDEX = 1;
	public static final int GET_CHILDREN_EVENT_INDEX = 2;
	public static final int GET_CONTENT_STREAM_INDEX = 3;

	/**
	 * Initializes the wrapper with a set of optional parameters from the
	 * properties file
	 */
	@Override
	public void initialize(Object[] params) {

		// Whenever CmisServiceWrapperManager.wrap() is called, a new wrapper
		// instance is created and the corresponding initialize() method is
		// called.
		// If the service factory creates a new CmisService object and wraps it
		// with every request, then new wrapper instances are created and
		// initialized for every request.
		// This is why in this case you will see initialize get called for every
		// request.
		// Depending on your implementation you may only see this called once.

		// initialize the PCH listener stuff
		// add a sync block since this could be re-entered while processing first time
		synchronized(events) {
			if (listener == null) {
				listener = new Listener("CMIS V 1.1 PCH server extension", "0.1",
						null, true);
	
				// setup all of the other objects now as well
				cmisContainer = listener.lookupContainer("cmisContainer");
	
				// this one will be a catch all for any requests
				// (unfiltered by type)
				events[ALL_REQUESTS_INDEX] = cmisContainer.lookupEvent(
						PCHeventClass.RPC, "allEvents");
	
				/**
				 * Example of how to manually set up the events :
				 *  
				 * // filtered events events[GET_CHILDREN_EVENT_INDEX] =
				 * cmisContainer.lookupEvent( PCHeventClass.RPC, "getChildren");
				 * events[QUERY_EVENT_INDEX] = cmisContainer.lookupEvent(
				 * PCHeventClass.RPC, "query"); events[GET_CONTENT_STREAM_INDEX] =
				 * cmisContainer.lookupEvent( PCHeventClass.RPC,
				 * "getContentStream");
				 * 
				 * // create array for events and durations // one for each CMIS
				 * operation durations[GET_CHILDREN_EVENT_INDEX] =
				 * events[GET_CHILDREN_EVENT_INDEX]
				 * .lookupAccumulator("getChildrenTime");
				 * durations[QUERY_EVENT_INDEX] = events[QUERY_EVENT_INDEX]
				 * .lookupAccumulator("queryTime");
				 * durations[GET_CONTENT_STREAM_INDEX] =
				 * events[GET_CONTENT_STREAM_INDEX]
				 * .lookupAccumulator("getContentStreamTime");
				 * 
				 * Loop below handles any that you put in the events list automatically
				 */
	
				// starting at 1 since we want to skip the allEvents event.
				for (int i = 1; i < events.length; i++) {
	
					String tempEvent = eventNames[i];
					System.out.println("setting up event:" + tempEvent);
					// setup all the event objects
					events[i] = cmisContainer.lookupEvent(PCHeventClass.RPC, tempEvent);
					// and their matching accumulator named xxxTime.
					durations[i] = events[i].lookupAccumulator(tempEvent + "Time");
				}
			}
		} // sync block
		// Record this event - whatever type it might be
		// All requests will come through the init method. 
		events[ALL_REQUESTS_INDEX].recordEvent();
	}

	// provide constructor (abstract base)
	public BasicPchListener(CmisService service) {
		super(service);

	}

	@Override
	public ObjectList query(String repositoryId, String statement,
			Boolean searchAllVersions, Boolean includeAllowableActions,
			IncludeRelationships includeRelationships, String renditionFilter,
			BigInteger maxItems, BigInteger skipCount, ExtensionsData extension) {

		Duration dur = listener.durationFactory(durations[QUERY_EVENT_INDEX]);

		dur.start();
		ObjectList retVal = getWrappedService().query(repositoryId, statement,
				searchAllVersions, includeAllowableActions,
				includeRelationships, renditionFilter, maxItems, skipCount,
				extension);

		// passing true here would cause the associated event to record +1
		dur.stop(true);
		return retVal;
	}

	@Override
	public ObjectInFolderList getChildren(String repositoryId, String folderId,
			String filter, String orderBy, Boolean includeAllowableActions,
			IncludeRelationships includeRelationships, String renditionFilter,
			Boolean includePathSegment, BigInteger maxItems,
			BigInteger skipCount, ExtensionsData extension) {

		Duration dur = listener
				.durationFactory(durations[GET_CHILDREN_EVENT_INDEX]);

		dur.start();
		ObjectInFolderList retVal = getWrappedService().getChildren(
				repositoryId, folderId, filter, orderBy,
				includeAllowableActions, includeRelationships, renditionFilter,
				includePathSegment, maxItems, skipCount, extension);

		// passing true here will cause the associated event to record +1
		dur.stop(true);
		return retVal;
	}

	@Override
	public ContentStream getContentStream(String repositoryId, String objectId,
			String streamId, BigInteger offset, BigInteger length,
			ExtensionsData extension) {

		Duration dur = listener
				.durationFactory(durations[GET_CONTENT_STREAM_INDEX]);

		dur.start();
		ContentStream retVal = getWrappedService().getContentStream(
				repositoryId, objectId, streamId, offset, length, extension);
		dur.stop(true);

		return retVal;
	}
}

