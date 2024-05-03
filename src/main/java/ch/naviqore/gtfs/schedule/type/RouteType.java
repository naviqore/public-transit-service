package ch.naviqore.gtfs.schedule.type;

/**
 * Provides a unified approach to handling different modes of transportation of routes within a GTFS feed. Implementing
 * this interface allows for retrieval of both unique identifier codes and descriptions of transportation route types.
 *
 * @author munterfi
 */
public interface RouteType {

    /**
     * Parses a string to the corresponding RouteType: Either default GTFS route type or Hierarchical Vehicle Type
     * (HVT).
     *
     * @param code the string code to parse
     * @return the corresponding RouteType
     * @throws NumberFormatException    if the code is not a valid integer
     * @throws IllegalArgumentException if the code is negative or invalid
     */

    static RouteType parse(String code) {
        return parse(Integer.parseInt(code));
    }

    static RouteType parse(int code) {
        if (code < 0) {
            throw new IllegalArgumentException("Invalid negative RouteType code: " + code);
        }
        if (code <= 12) {
            return DefaultRouteType.parse(code);
        } else {
            return HierarchicalVehicleType.parse(code);
        }
    }

    /**
     * Retrieves the code associated with the route type.
     */
    int getCode();

    /**
     * Retrieves a description of the route type.
     */
    String getDescription();

}
