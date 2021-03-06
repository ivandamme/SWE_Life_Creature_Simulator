package ModelPackage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Uses a simplified version of Dijkstra's pathfinding algorithm to find the shortest path to a certain position.
 * Also provides information on where to navigate to. Assumes the following Grid setup:
 * <p>
 * 6 7 8
 * 3 4 5
 * 0 1 2
 */
public class MovementPlanner {

    private IGrid simulationGrid;
    private ArrayList<MotionPoint> planableGrid;

    private ArrayList<ArrayList<MotionPoint>> subGrids;

    public MovementPlanner() {
    }

    /**
     * Initializes the planner with the correct data and triggers the generation of a complete plannable grid.
     *
     * @param simulationGrid The simulation Grid used to generate a plannable grid
     * @return true if the generation of the plannable grid was successful, false otherwise.
     */
    public boolean initializePlanner(IGrid simulationGrid) {
        this.simulationGrid = simulationGrid;
        this.planableGrid = new ArrayList<>();

        try {
            generatePlannableGrid();
        } catch (Exception e) {
            System.out.println("Error while generating plannable grid.");
            return false;
        }

        try {
            generateSubgrids();
        } catch (Exception e) {
            System.out.println("Error generating subgrids");
            return false;
        }
        return true;
    }

    /**
     * Generates subgrids to optimize motionplanning by substracting the water from the islands
     */
    private void generateSubgrids() {
        subGrids = new ArrayList<>();

        ArrayList<MotionPoint> waterList = new ArrayList<>();
        //waterlist is always element 0 in the subgrid list
        subGrids.add(waterList);
        for (MotionPoint motionPoint : planableGrid) {
            if (motionPoint.getType() == GridPointType.Water) {
                waterList.add(motionPoint);
            } else {
                if (motionPoint.getType() == GridPointType.Obstacle) {
                    continue;
                }
                //we found a living area, check if we found this one already
                boolean found = false;
                for (ArrayList<MotionPoint> area : subGrids) {
                    if (area.contains(motionPoint)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    ArrayList<MotionPoint> livingArea = new ArrayList<>();
                    livingArea.add(motionPoint);
                    ArrayList<Point> openPoints = new ArrayList<>();
                    for (Point adjacent : motionPoint.getAdjacentPoints()) {
                        if (getMotionPointByCoordinates(adjacent).getType() != GridPointType.Obstacle) {
                            openPoints.add(adjacent);
                        }
                    }

                    ArrayList<Point> pointBuffer = new ArrayList<>();
                    while (openPoints.size() > 0) {
                        for (Point p : openPoints) {
                            MotionPoint currentPoint = getMotionPointByCoordinates(p);
                            //if ((!livingArea.contains(currentPoint)) && (!currentPoint.getWater())){
                            if ((!livingArea.contains(currentPoint)) && (currentPoint.getType() == GridPointType.Ground)) {
                                livingArea.add(currentPoint);
                                for (Point nextPoint : currentPoint.getAdjacentPoints()) {
                                    if (!pointBuffer.contains(nextPoint)) {
                                        pointBuffer.add(nextPoint);
                                    }
                                }
                            }
                        }

                        openPoints = new ArrayList<>(pointBuffer);
                        pointBuffer.clear();
                    }
                    subGrids.add(livingArea);
                }
            }
        }


        System.out.println("Points in water list: " + waterList.size());
        System.out.println("Subgrids Found: " + subGrids.size());
    }

    /**
     * Used to generate the plannable grid, consisting of MotionPoints and the points adjacent to them. If this fails the motionplanner can't properly function.
     */
    private void generatePlannableGrid() {
        if (simulationGrid != null) {
            int pointCount = simulationGrid.getWidth() * simulationGrid.getHeight();
            for (int i = 0; i < pointCount; i++) {
                planableGrid.add(null);
            }
            //create a MotionPoint for each GridPoint
            for (GridPoint gridPoint : simulationGrid.getPointList()) {
                int gridPointNumber = getPointNumber(new Point(gridPoint.getX(), gridPoint.getY()));

                MotionPoint freshMotionPoint = new MotionPoint(gridPoint);
                planableGrid.set(gridPointNumber, freshMotionPoint);
            }

            for (MotionPoint currentPoint : planableGrid) {
                getAdjacentPoints(currentPoint);
            }
        } else {
            throw new NullPointerException("SimulationGrid was not set!");
        }
    }

    /**
     * Gets the total count of MotionPoints in the planableGrid.
     *
     * @return int containing the MotionPoint count
     */
    public int getTotalMotionPoints() {
        return planableGrid.size();
    }

    /**
     * Gets the total adjacent points found in the plannable grid
     *
     * @return int containing the adjacent point count
     */
    public int getTotalAdjacentCount() {
        int counter = 0;
        for (MotionPoint mp : planableGrid) {
            counter += mp.adjacentPoints.size();
        }
        return counter;
    }

    /**
     * Find the points adjacent to the current point
     *
     * @param currentPoint point to find the adjacent points of and store them in.
     */
    private void getAdjacentPoints(MotionPoint currentPoint) {

        int x = currentPoint.getX();
        int y = currentPoint.getY();

        //get point list
        for (int xx = 1; xx >= -1; xx--) {
            for (int yy = 1; yy >= -1; yy--) {
                int neighbourX = x - xx;
                int neighbourY = y - yy;
                if (neighbourX < 0) {
                    //x falls left of grid
                    neighbourX = simulationGrid.getWidth() - 1;
                }
                if (neighbourX > simulationGrid.getWidth() - 1) {
                    //x falls of right of grid
                    neighbourX = 0;
                }
                if (neighbourY < 0) {
                    //y falls of below grid
                    neighbourY = simulationGrid.getHeight() - 1;
                }
                if (neighbourY > simulationGrid.getHeight() - 1) {
                    neighbourY = 0;
                }
                //add this point to the adjacentpoints, only if the point is not itself (x+0 && y+0)
                if (!((neighbourX == x) && (neighbourY == y))) {
                    if (getMotionPointByCoordinates(new Point(neighbourX, neighbourY)).getType() != GridPointType.Obstacle) {
                        currentPoint.addAdjacentPoint(new Point(neighbourX, neighbourY));
                    }
                }
            }
        }
    }


    /**
     * Generate a path towards the endpoint
     *
     * @param startPoint  First point of the path
     * @param targetPoint Last point of the path
     * @return ArrayList of points, in the right order that lead to the endpoint. Returns null if no path was found or
     * the startpoint was the endpoint
     */
    public ArrayList<Point> findPath(Point startPoint, Point targetPoint, Boolean useIslandOnlyNavigation) throws Exception {
        //reset the grid, clearing all previous points
        resetPlannableGrid();

        if ((startPoint.getX() == targetPoint.getX()) && (startPoint.getY() == targetPoint.getY()) &&
                (simulationGrid.getPointType(targetPoint) != GridPointType.Obstacle) && (simulationGrid.getPointType(startPoint) != GridPointType.Obstacle)) {
            return null;
        }

        long startTime = System.nanoTime();

        //points to be checked in this iteration
        ArrayList<Point> openPoints = new ArrayList<>();
        //points to be checked in the next iteration
        ArrayList<Point> pointBuffer = new ArrayList<>();
        //points already checked
        ArrayList<Point> closedPoints = new ArrayList<>();
        //Livingarea for the startpoint
        ArrayList<MotionPoint> primarySearchArea = new ArrayList<>();
        //Buffer for points not in the primarySearchArea
        ArrayList<Point> secondaySearchAreaBuffer = new ArrayList<>();

        /*for (ArrayList<MotionPoint> area : subGrids) {
            //TODO: Only use a primary search area if the area is not the water!
            if (area.contains(getMotionPointByCoordinates(startPoint))) {
                primarySearchArea = area;
            }
        }*/

        if (useIslandOnlyNavigation){
            if (subGrids.get(0).contains(getMotionPointByCoordinates(startPoint))){
                primarySearchArea = planableGrid;
            }
            else{
                for (ArrayList<MotionPoint> area : subGrids) {
                    //TODO: Only use a primary search area if the area is not the water!
                    if (area.contains(getMotionPointByCoordinates(startPoint))) {
                        primarySearchArea = area;
                    }
                }
            }
        }
        else{
            //creature wants to be able to swim. Make it do so.
            primarySearchArea = planableGrid;
        }

        //fetch the first set of adjacent points to the startpoint
        closedPoints.add(startPoint);
        for (Point adjacentPoint : getMotionPointByCoordinates(startPoint).getAdjacentPoints()) {
            MotionPoint freshPoint = getMotionPointByCoordinates(adjacentPoint);
            freshPoint.setPreviousPoint(startPoint);
            if (primarySearchArea.contains(freshPoint)) {
                openPoints.add(adjacentPoint);
            } else {
                secondaySearchAreaBuffer.add(adjacentPoint);
            }
        }

        int distanceCounter = 0;
        //output debug image for start situation
        debugGrid(distanceCounter, startPoint, targetPoint, openPoints, closedPoints);
        MotionPoint endPoint = null;
        boolean searchInPrimary = true;

        while (endPoint == null) {
            if ((openPoints.size() == 0) && (secondaySearchAreaBuffer.size() == 0)) {
                return null;
            }

            //check the open points for the endpoint
            endPoint = checkForTarget(openPoints, targetPoint);
            if (endPoint != null) {
                System.out.println("Found target. Steps required: " + distanceCounter);
                //output debug image for endstate
                debugGrid(distanceCounter, startPoint, targetPoint, openPoints, closedPoints);
            } else {
                //point not found, move current points into closed points and fill buffer with new points
                for (Point currentPoint : openPoints) {
                    MotionPoint motionPoint = getMotionPointByCoordinates(currentPoint);
                    for (Point freshOpenPoint : motionPoint.getAdjacentPoints()) {
                        //only add to pointbuffer if not already in other list
                        if ((!closedPoints.contains(freshOpenPoint)) && (!openPoints.contains(freshOpenPoint)) && (!pointBuffer.contains(freshOpenPoint)) && (!secondaySearchAreaBuffer.contains(freshOpenPoint))) {
                            MotionPoint freshMP = getMotionPointByCoordinates(freshOpenPoint);
                            if ((freshMP.getPreviousPoint() == null)) {
                                freshMP.setPreviousPoint(currentPoint);
                            }
                            if (primarySearchArea.contains(freshMP)) {
                                pointBuffer.add(freshOpenPoint);
                            } else {
                                secondaySearchAreaBuffer.add(freshOpenPoint);
                            }
                        }
                    }
                }
            }

            for (Point openPoint : openPoints) {
                if (!closedPoints.contains(openPoint)) {
                    closedPoints.add(openPoint);
                }
            }

            if (pointBuffer.size() == 0) {
                //no new points in primary area, start getting points from secondary area
                searchInPrimary = false;
            }
            //target not found, move all open points to closed points and move the buffer into the open points
            if (searchInPrimary) {
                openPoints = new ArrayList<>(pointBuffer);
            } else {
                openPoints = new ArrayList<>(secondaySearchAreaBuffer);
            }
            pointBuffer.clear();
            distanceCounter++;
            debugGrid(distanceCounter, startPoint, targetPoint, openPoints, closedPoints);
        }

        long endTime = System.nanoTime();
        System.out.println("Pathfinding completed in " + ((endTime - startTime) / 1000000) + "ms");

        return getPathFound(endPoint);
    }

    /**
     * check if a certain motionPoint is the target for this motionfinding round
     *
     * @param openPoints  List of points to be checked
     * @param targetPoint target location of the motionplanner
     * @return MotionPoint if found, null if not found
     */
    private MotionPoint checkForTarget(ArrayList<Point> openPoints, Point targetPoint) {
        for (Point currentPoint : openPoints) {
            if ((currentPoint.getX() == targetPoint.getX()) && (currentPoint.getY() == targetPoint.getY())) {
                MotionPoint found = getMotionPointByCoordinates(currentPoint);
                return found;
            }
        }
        return null;
    }

    /**
     * calulates the path back from the endpoint to the startpoint
     *
     * @param endPoint MotionPoint that represents the last point in the pathfinding sequence
     * @return ArrayList cointainting the path back from the endpoint to the startpoint, including both
     */
    private ArrayList<Point> getPathFound(MotionPoint endPoint) {
        ArrayList<Point> pathFound = new ArrayList<>();
        MotionPoint parentPoint = endPoint;
        int infiniteProtection = 0;
        while ((parentPoint != null) && (infiniteProtection < 100)) {
            infiniteProtection++;

            pathFound.add(new Point(parentPoint.getX(), parentPoint.getY()));
            if (parentPoint.getPreviousPoint() != null) {
                parentPoint = getMotionPointByCoordinates(parentPoint.getPreviousPoint());
            } else {
                break;
            }
        }
        return pathFound;
    }

    /**
     * Resets the pathFound and previousPoint references so the plannable grid can be reused, reducing the time needed
     * for pathfinding
     */
    private void resetPlannableGrid() {
        for (MotionPoint mp : planableGrid) {
            mp.setPreviousPoint(null);
        }
    }

    /**
     * Gets the element number corresponding to this points coordinates.
     *
     * @param p Point to be used
     * @return int element number in the pointList
     */
    private int getPointNumber(Point p) {
        int gridWidth = simulationGrid.getWidth();
        int x = (int) p.getX();
        int y = (int) p.getY();
        int number = (gridWidth * (y + 1)) + (x - gridWidth);
        //System.out.println(number);
        return number;
    }

    /**
     * Gets the MotionPoint corresponding to a coordinate
     *
     * @param p Point to fetch the associated MotionPoint of
     * @return MotionPoint corresponding to the point
     */
    private MotionPoint getMotionPointByCoordinates(Point p) {
        if (planableGrid != null) {
            //point element number (n) in array can be calculated by formula: (width * (y+1)) + (x - width). Width being the grid width
            return planableGrid.get(((simulationGrid.getWidth() * ((int) p.getY() + 1)) + ((int) p.getX() - simulationGrid.getWidth())));
        } else {
            throw new NullPointerException("No Plannable Grid to fetch points from!");
        }
    }

    /**
     * Returns the livingareas found by the motionplanner so the world can randomly place the creatures as defined
     * in the requirements. Item 0 in the returned list is always the surrounding water.
     * DO NOT CALL BEFORE CALLING intializePlanner()!
     *
     * @return
     */
    public ArrayList<ArrayList<Point>> getLivingAreas() throws Exception {
        if (!(subGrids.size() > 0)) {
            throw new Exception("Planner not properly initialized");
        }
        ArrayList pointAreas = new ArrayList();
        for (ArrayList<MotionPoint> area : subGrids) {
            //convert motionpoints to regular points since MotionPoint is an Inner class that World is unaware of
            ArrayList<Point> pointArea = new ArrayList();
            for (MotionPoint motionPoint : area) {
                pointArea.add(new Point(motionPoint.getX(), motionPoint.getY()));
            }
            pointAreas.add(pointArea);
        }
        return pointAreas;
    }

    /**
     * Debugging method for generating a visual image of the planableGrid.
     *
     * @param stepNumber   int representing the number of steps in the motionplanning so far
     * @param startPoint   Point representing the start position of the motionplanning
     * @param endPoint     Point representing the end position (not to be confused with the endpoint used internally in the findPath method!)
     * @param openPoints   ArrayList of points containing the open Points
     * @param closedPoints ArrayList of points containing the closed Points
     */
    private void debugGrid(int stepNumber, Point startPoint, Point endPoint, ArrayList<Point> openPoints, ArrayList<Point> closedPoints) {
        int factor = 20;
        int size = 5;

        int canvasWidth = simulationGrid.getWidth() * factor;
        int canvasHeight = simulationGrid.getHeight() * factor;

        BufferedImage img = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setBackground(Color.lightGray);
        g2.setPaint(Color.lightGray);
        g2.fillRect(0, 0, canvasWidth, canvasHeight);


        //draw grid
        int gridWidth = simulationGrid.getWidth();
        int gridHeight = simulationGrid.getHeight();
        for (int i = 0; i < gridWidth; i++) {
            for (int j = 0; j < gridHeight; j++) {
                switch (getMotionPointByCoordinates(new Point(i, j)).getType()) {
                    case Obstacle:
                        g2.setColor(Color.MAGENTA);
                        break;
                    case Water:
                        g2.setColor(Color.CYAN);
                        break;
                    case Ground:
                        g2.setColor(Color.ORANGE);
                        break;
                }
                g2.fillOval(i * factor, j * factor, size, size);
            }
        }


        //draw open points
        for (Point openPoint : openPoints) {
            g2.setPaint(Color.BLACK);
            g2.fillOval((int) openPoint.getX() * factor, (int) openPoint.getY() * factor, size, size);
        }

        //draw closed points
        for (Point closedPoint : closedPoints) {
            g2.setColor(Color.ORANGE);
            g2.drawOval((int) closedPoint.getX() * factor, (int) closedPoint.getY() * factor, size, size);
            g2.setColor(Color.BLUE);
            g2.setStroke(new BasicStroke(2));
            //if ((closedPoint.getX() != (int)startPoint.getX()) && (closedPoint.getY() != (int)startPoint.getY())){
            try {
                Point previousPoint = getMotionPointByCoordinates(closedPoint).getPreviousPoint();
                g2.drawLine((int) closedPoint.getX() * factor, (int) closedPoint.getY() * factor, (int) previousPoint.getX() * factor, (int) previousPoint.getY() * factor);
            } catch (NullPointerException ne) {

            }
        }
        //draw endpoint
        g2.setPaint(Color.RED);
        g2.fillOval((int) endPoint.getX() * factor, (int) endPoint.getY() * factor, size, size);


        try {
            //ImageIO.write(img, "PNG", new File("/out/buffer_output"+stepNumber+".png"));
            ImageIO.write(img, "PNG", new File(System.getProperty("user.dir") + File.separator + "out" + File.separator + "buffer_output" + stepNumber + ".png"));
            //System.out.println("Wrote img to disk");
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    /**
     * Inner class used to create a version of the grid suitable for use in motion planning.
     * These points are "aware" of their neighbouring points.
     */
    private class MotionPoint {

        private boolean isObstacle;

        private GridPoint gridPoint;
        private Point previousPoint;
        private ArrayList<Point> adjacentPoints;

        public MotionPoint(GridPoint gridPoint) {
            previousPoint = null;
            adjacentPoints = new ArrayList<>();
            isObstacle = false;
            this.gridPoint = gridPoint;
        }

        /**
         * Sets whether or not the point is an obstacle
         *
         * @param isObstacle boolean true or false
         */
        public void setObstacle(boolean isObstacle) {
            this.isObstacle = isObstacle;
        }

        /**
         * returns whether or not the point is an obstacle
         *
         * @return boolean whether or not this is an obstacle
         */
        @Deprecated
        public boolean getObstacle() {
            return this.isObstacle;
        }

        /**
         * Sets the previous point in the used path when generating a path
         *
         * @param point A point in the plannable grid
         */
        public void setPreviousPoint(Point point) {
            this.previousPoint = point;
        }

        /**
         * Gets the previous point in the used path when generating a path
         *
         * @return Returns the previous point in the plannable grid
         */
        public Point getPreviousPoint() {
            return previousPoint;
        }

        /**
         * Adds an adjacent point in the plannable grid
         *
         * @param point a motionpoint instance in the plannable grid
         */
        public void addAdjacentPoint(Point point) {
            adjacentPoints.add(point);
        }

        /**
         * Fetch a list with all adjacent points in the plannable grid
         *
         * @return ArrayList containing MotionPoints
         */
        public ArrayList<Point> getAdjacentPoints() {
            return adjacentPoints;
        }

        /**
         * Gets the GridPoint representing this MotionPoint in the regular simulation grid
         *
         * @return
         */
        public GridPoint getGridPoint() {
            return gridPoint;
        }

        /**
         * Gets the X coordinate, based on the regular grid system
         *
         * @return X coordinate
         */
        public int getX() {
            return gridPoint.getX();
        }

        /**
         * Gets the Y coordinate, based on the regular grid system
         *
         * @return Y coordinate
         */
        public int getY() {
            return gridPoint.getY();
        }

        /**
         * Gets the type of gridPoint
         *
         * @return gridPointType enum
         */
        public GridPointType getType() {
            return gridPoint.getType();
        }
    }

}