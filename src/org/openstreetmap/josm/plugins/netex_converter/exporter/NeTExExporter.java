/* 
 * Copyright (C) 2020 Labian Gashi
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */
package org.openstreetmap.josm.plugins.netex_converter.exporter;

import org.apache.log4j.Logger;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

import com.netex.model.*;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import javax.swing.JOptionPane;

import jaxb.CustomMarshaller;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.netex_converter.util.OSMHelper;
import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.gui.MapView;
import static org.openstreetmap.josm.gui.NavigatableComponent.PROP_SNAP_DISTANCE;
import org.openstreetmap.josm.plugins.netex_converter.model.Elevator;
import org.openstreetmap.josm.plugins.netex_converter.model.FootPath;
import org.openstreetmap.josm.plugins.netex_converter.model.Steps;
import org.openstreetmap.josm.plugins.netex_converter.util.OSMTags;

/**
 *
 * @author Labian Gashi
 */
public class NeTExExporter {

    private final static Logger LOGGER = Logger.getLogger(NeTExExporter.class);
    private final NeTExParser neTExParser;
    private final ObjectFactory neTExFactory;
    private final CustomMarshaller customMarshaller;
    private final net.opengis.gml._3.ObjectFactory gmlFactory;
    private final HashMap<Node, StopPlace> stopPlaces;
    private final HashMap<Node, Quay> quays;
    private final HashMap<Node, Elevator> elevators;
    private final HashMap<Way, Steps> steps;
    private final HashMap<Way, FootPath> footPaths;
    private final DataSet ds;
    //private final NeTExValidator neTExValidator = NeTExValidator.getNeTExValidator();

    public NeTExExporter() {
        neTExParser = new NeTExParser();
        neTExFactory = new ObjectFactory();
        gmlFactory = new net.opengis.gml._3.ObjectFactory();
        customMarshaller = new CustomMarshaller(PublicationDeliveryStructure.class);
        stopPlaces = new HashMap<>();
        quays = new HashMap<>();
        elevators = new HashMap<>();
        steps = new HashMap<>();
        footPaths = new HashMap<>();
        ds = MainApplication.getLayerManager().getActiveDataSet();
    }

    public void exportToNeTEx(File neTExFile) {
        Collection<OsmPrimitive> primitives = null;

        if (ds == null) {
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(), tr("No data has been loaded into JOSM"));
            return;
        }
        else {
            primitives = ds.allNonDeletedPrimitives();
        }

        for (OsmPrimitive primitive : primitives) {
            if (primitive instanceof Node) {
                Node node = (Node) primitive;

                if (OSMHelper.isTrainStation(node)) {

                    stopPlaces.put(node, neTExParser.createStopPlace(node, StopTypeEnumeration.RAIL_STATION));
                }
                else if (OSMHelper.isBusStation(node)) {
                    stopPlaces.put(node, neTExParser.createStopPlace(node, StopTypeEnumeration.BUS_STATION));
                }
                else if (OSMHelper.isBusStop(node)) {
                    stopPlaces.put(node, neTExParser.createStopPlace(node, StopTypeEnumeration.ONSTREET_BUS));
                }
                else if (OSMHelper.isPlatform(node)) {
                    quays.put(node, neTExParser.createPlatform(node));
                }
                else if (OSMHelper.isElevator(node)) {
                    elevators.put(node, neTExParser.createElevator(node));
                }
                else {

                }

            }
            else if (primitive instanceof Way) {
                Way way = (Way) primitive;
                if (OSMHelper.isSteps(way)) {
                    steps.put(way, neTExParser.createSteps(way));
                }
                else if (OSMHelper.isFootPath(way)) {
                    footPaths.put(way, neTExParser.createFootPath(way));
                }
                else if (OSMHelper.isPlatform(way)) {

                }
            }
            else if (primitive instanceof Relation) {

            }
            else {
                LOGGER.warn(tr("The OSM primitive type could not be determined."));
            }
        }

        for (HashMap.Entry<Node, StopPlace> stopEntry : stopPlaces.entrySet()) {
            StopPlace stopPlace = stopEntry.getValue();
            boolean childrenAvailable = false;

            Quays_RelStructure currentQuays = new Quays_RelStructure();

            for (HashMap.Entry<Node, Quay> quayEntry : quays.entrySet()) {
                String quayUicRef = OSMHelper.getUicRef(quayEntry.getKey());
                String stopUicRef = OSMHelper.getUicRef(stopEntry.getKey());

                String nodeRef = quayEntry.getKey().getKeys().containsKey(OSMTags.REF_TAG) ? quayEntry.getKey().getKeys().get(OSMTags.REF_TAG) : null;

                if (quayUicRef != null && !quayUicRef.trim().isEmpty() && stopUicRef != null && !stopUicRef.trim().isEmpty()) {
                    if (quayUicRef.equals(stopUicRef)) {
                        currentQuays.withQuayRefOrQuay(Arrays.asList(quayEntry.getValue()
                                .withId(String.format("ch:1:Quay:%1$s:%2$s", quayUicRef, nodeRef))
                                .withPublicCode(nodeRef)));
                        childrenAvailable = true;
                    }
                }
                else {
                    /*Point point = MainApplication.getMap().mapView.getPoint(entry.getValue().getCoor());
                    Node nearestNode = MainApplication.getMap().mapView.getNearestNode(point, OsmPrimitive::isTagged, false);
                    ... */
                }
            }

            if (childrenAvailable) {
                stopPlaces.replace(stopEntry.getKey(), stopPlace.withQuays(currentQuays));
            }
        }

        for (HashMap.Entry<Node, StopPlace> stopPlaceEntry : stopPlaces.entrySet()) {
            boolean childrenAvailable = false;
            long stopPlaceNodeId = stopPlaceEntry.getKey().getId();

            SitePathLinks_RelStructure currentPathLinks = new SitePathLinks_RelStructure();
            PathJunctions_RelStructure currentPathJunctions = new PathJunctions_RelStructure();
            EquipmentPlaces_RelStructure currentEquipmentPlaces = new EquipmentPlaces_RelStructure();

            for (HashMap.Entry<Node, Elevator> elevatorEntry : elevators.entrySet()) {
                LatLon coord = elevatorEntry.getKey().getCoor();
                Node closestStopPlace = findNearestMatchingStopPlace(coord, stopPlaceNodeId);

                if (closestStopPlace != null) {
                    currentEquipmentPlaces.withEquipmentPlaceRefOrEquipmentPlace(Arrays.asList(elevatorEntry.getValue().getEquipmentPlace()));
                    currentPathJunctions.withPathJunctionRefOrPathJunction(Arrays.asList(elevatorEntry.getValue().getPathJunction()));

                    childrenAvailable = true;
                }
            }

            for (HashMap.Entry<Way, Steps> stepsEntry : steps.entrySet()) {
                LatLon coord = stepsEntry.getKey().firstNode().getCoor();
                Node closestStopPlace = findNearestMatchingStopPlace(coord, stopPlaceNodeId);

                if (closestStopPlace != null) {
                    for (PathJunction pathJunction : stepsEntry.getValue().getPathJunctions()) {
                        currentPathJunctions.withPathJunctionRefOrPathJunction(Arrays.asList(pathJunction));
                    }
                    currentPathLinks.withPathLinkRefOrSitePathLink(Arrays.asList(stepsEntry.getValue().getSitePathLink()));
                    currentEquipmentPlaces.withEquipmentPlaceRefOrEquipmentPlace(Arrays.asList(stepsEntry.getValue().getEquipmentPlace()));

                    childrenAvailable = true;
                }
            }

            for (HashMap.Entry<Way, FootPath> footPathEntry : footPaths.entrySet()) {
                LatLon coord = footPathEntry.getKey().firstNode().getCoor();
                Node closestStopPlace = findNearestMatchingStopPlace(coord, stopPlaceNodeId);

                if (closestStopPlace != null) {
                    for (PathJunction pathJunction : footPathEntry.getValue().getPathJunctions()) {
                        currentPathJunctions.withPathJunctionRefOrPathJunction(Arrays.asList(pathJunction));
                    }
                    currentPathLinks.withPathLinkRefOrSitePathLink(Arrays.asList(footPathEntry.getValue().getSitePathLink()));
                    childrenAvailable = true;
                }
            }

            if (childrenAvailable == true) {
                stopPlaces.replace(stopPlaceEntry.getKey(), stopPlaceEntry.getValue()
                        .withEquipmentPlaces(currentEquipmentPlaces)
                        .withPathJunctions(currentPathJunctions)
                        .withPathLinks(currentPathLinks));
            }
        }

        if (stopPlaces.isEmpty()) {
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(), tr("File has not been exported because no stop places have been found in the currently loaded data."));
            return;
        }

        ArrayList<StopPlace> stopPlacesAsList = new ArrayList<>(stopPlaces.values());

        ResourceFrame resourceFrame = neTExParser.createResourceFrame();
        SiteFrame siteFrame = neTExParser.createSiteFrame(stopPlacesAsList);

        CompositeFrame compositeFrame = neTExParser.createCompositeFrame(resourceFrame, siteFrame);

        PublicationDeliveryStructure publicationDeliveryStructure = neTExParser.createPublicationDeliveryStsructure(compositeFrame);

        customMarshaller.marshal(neTExFactory.createPublicationDelivery(publicationDeliveryStructure), neTExFile);
    }

    public List<Node> getAllNearestNodes(Point p, Predicate<OsmPrimitive> predicate) {
        List<Node> nearestList = new ArrayList<>();

        for (List<Node> nlist : getNearestNodesImpl(p, predicate).values()) {
            nearestList.addAll(nlist);
        }

        return nearestList;
    }

    private Node findNearestStopPlace(LatLon coord) {
        Point p = MainApplication.getMap().mapView.getPoint(coord);
        Map<Double, List<Node>> dist_nodes = getNearestNodesImpl(p, OsmPrimitive::isTagged);
        Double[] distances = dist_nodes.keySet().toArray(new Double[0]);
        Arrays.sort(distances);
        Integer distanceIndex = -1;

        while (++distanceIndex < distances.length) {
            List<Node> nodes = dist_nodes.get(distances[distanceIndex]);

            for (Node node : nodes) {
                if (OSMHelper.isBusStation(node) || OSMHelper.isBusStop(node) || OSMHelper.isTrainStation(node)) {
                    return node;
                }
            }
        }

        return null;
    }

    private Node findNearestMatchingStopPlace(LatLon coord, long matchingId) {
        Point p = MainApplication.getMap().mapView.getPoint(coord);
        Map<Double, List<Node>> dist_nodes = getNearestNodesImpl(p, OsmPrimitive::isTagged);
        Double[] distances = dist_nodes.keySet().toArray(new Double[0]);
        Arrays.sort(distances);
        Integer distanceIndex = -1;

        while (++distanceIndex < distances.length) {
            List<Node> nodes = dist_nodes.get(distances[distanceIndex]);

            for (Node node : nodes) {
                if (node.getId() == matchingId && (OSMHelper.isBusStation(node) || OSMHelper.isBusStop(node) || OSMHelper.isTrainStation(node))) {
                    return node;
                }
            }
        }

        return null;
    }

    private Map<Double, List<Node>> getNearestNodesImpl(Point p, Predicate<OsmPrimitive> predicate) {
        Map<Double, List<Node>> nearestMap = new TreeMap<>();
        MapView mapView = MainApplication.getMap().mapView;

        if (ds != null) {
            double dist, snapDistanceSq = PROP_SNAP_DISTANCE.get();
            snapDistanceSq *= snapDistanceSq;

            for (Node n : ds.searchNodes(getBBox(p, PROP_SNAP_DISTANCE.get()))) {
                if (predicate.test(n)
                        && (dist = mapView.getPoint2D(n).distanceSq(p)) < snapDistanceSq) {
                    nearestMap.computeIfAbsent(dist, k -> new LinkedList<>()).add(n);
                }
            }
        }

        return nearestMap;
    }

    private BBox getBBox(Point p, int snapDistance) {
        MapView mapView = MainApplication.getMap().mapView;
        return new BBox(mapView.getLatLon(p.x - snapDistance, p.y - snapDistance),
                mapView.getLatLon(p.x + snapDistance, p.y + snapDistance));
    }

    public static boolean openXMLFile(final File file) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }

        Desktop desktop = Desktop.getDesktop();

        if (!desktop.isSupported(Desktop.Action.EDIT)) {
            return false;
        }

        try {
            desktop.edit(file);
        }
        catch (IOException e) {
            e.printStackTrace(System.out);
            return false;
        }

        return true;
    }
}
