package controller;

import jakarta.servlet.RequestDispatcher;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;

import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.LinkedHashMap;
import model.booking;
import model.timeslot;

public class timeSlotLogic extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private final String dbClass = System.getenv("DB_CLASS");
	private final String dbUrl = System.getenv("DB_URL");
	private final String dbPassword = System.getenv("DB_PASSWORD");
	private final String dbUser = System.getenv("DB_USER");
	
	public timeSlotLogic() {
        super();
    }	
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		System.out.println("We are in doGet of the timeslotPage");
		
		// Get data from the params
		String date = (String) request.getAttribute("date");
		String strServiceId = (String) request.getAttribute("serviceId");
		int serviceId = 0;
		
		System.out.println("--- Chosen Date: " + date);
		System.out.println("--- Chosen serviceId: " + strServiceId);
		
		if(date != null && strServiceId != null) {
			try{
				serviceId = Integer.parseInt(strServiceId);
			}
			catch(NumberFormatException e) {
				System.out.printf("Failed to parse to integer. ", e);
				response.sendRedirect(request.getContextPath() + "/pages/booking.jsp?errorMessage=Invalid service ID");
			}
		} else {
			System.out.println("Did not have the params");
			response.sendRedirect(request.getContextPath() + "/pages/booking.jsp?errorMessage=Does not have the necessary params");;
		}
		
		try {
			// Get the timeslot_id, duration_in_hour and price from 'service_timeslot' using service id and date
			Map<String, Object> smallInfo = getSmallInfoByServiceIdAndDate(date, serviceId);
			
			// Get all the time slots from the 'timeslot'
			List<Integer> timeslots = getTimeslotsByTimeslotId((int) smallInfo.get("timeslot_id"));
			
			// add lunch break to timeslots
			timeslots.add(4, 999);
			
			// check the duration of the service and format the data
			List<Integer> formattedTimeslots = formatTimeslots(timeslots, (int) smallInfo.get("duration"));
				// This array has either 0, 1, or 2. 
				// 0 if someome booked already,
				// 1 if the slot is available but not enough time for the service duration
				// 2 if the slot is available and behind got enough time for the service duration
			
			// pass the data to the frontend in linked hashmap
			Map<String, Object> timeslotAvailability = new LinkedHashMap<>();
			timeslotAvailability.put("booking_date", date);
			timeslotAvailability.put("8am-9am", formattedTimeslots.get(0));
        	timeslotAvailability.put("9am-10am", formattedTimeslots.get(1));
        	timeslotAvailability.put("10am-11am", formattedTimeslots.get(2));
        	timeslotAvailability.put("11am-12pm", formattedTimeslots.get(3));
        	timeslotAvailability.put("12pm-1pm", formattedTimeslots.get(4));
        	timeslotAvailability.put("1pm-2pm", formattedTimeslots.get(5));
        	timeslotAvailability.put("2pm-3pm", formattedTimeslots.get(6));
        	timeslotAvailability.put("3pm-4pm", formattedTimeslots.get(7));
        	timeslotAvailability.put("4pm-5pm", formattedTimeslots.get(8));
        	timeslotAvailability.put("5pm-6pm", formattedTimeslots.get(9));
			
        	// Store the list in the session attribute
        	HttpSession session = request.getSession(false);
		    session.setAttribute("timeslot-availability", timeslotAvailability);
		    
		    response.sendRedirect(request.getContextPath() + "/pages/timeSlotPage.jsp");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// Get the data from the frontend
		
		// set the tiemslot object,
	}
	
	
	
	private List<Integer> formatTimeslots(List<Integer> timeslots, int duration) {
		// Declarations
		List<Boolean> bool_timeslots = new ArrayList<>();
		List<Integer> formattedTimeslots = new ArrayList<>();
		
		
		// Change it to boolean list to make it easier to check along the way
		
		for(int i = 0; i < timeslots.size(); i++) {
			if(timeslots.get(i) == 0) {
				bool_timeslots.add(true);
			}
			else {
				bool_timeslots.add(false);
			}
        }
		
		
		// Start the process
		for (int i = 0; i < bool_timeslots.size(); i++) {
	        if (!bool_timeslots.get(i)) {
	            // If the current timeslot is false, mark as 0
	            formattedTimeslots.add(0);
	        } else {
	            // Check the next `duration` slots
	            boolean allTrue = true;
	            for (int j = i; j < i + duration && j < timeslots.size(); j++) {
	                if (!bool_timeslots.get(j)) {
	                    allTrue = false;
	                    break;
	                }
	            }

	            if (i + duration <= timeslots.size() && allTrue) {
	                // If we have enough slots and all are true
	                formattedTimeslots.add(2);
	            } else {
	                // Not enough slots or a false was encountered
	                formattedTimeslots.add(1);
	            }
	        }
	    }
		
		return formattedTimeslots;
	}
	
	// ===== Models =====
	private Map<String, Object> getSmallInfoByServiceIdAndDate(String date, int serviceId) throws SQLException {
		System.out.println("We are in functions to get the small data");
		// Set Class
		try {
			Class.forName(dbClass);
		} catch(ClassNotFoundException e) {
			System.out.printf("Connection drive issue.", e);
			e.printStackTrace();
		}
				
		Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
		
		String query = """
						SELECT 
							st.timeslot_id,
							s.service_name,
							s.duration_in_hour
						FROM 
							service_timeslot st
						JOIN
							service s ON st.service_id = s.service_id
						WHERE 
							st.service_id = ? AND 
							st.service_timeslot_date = ?;
						""";
		Map<String, Object> info = new HashMap<>();
		
		try (PreparedStatement pstmt = conn.prepareStatement(query)) {
			pstmt.setInt(1, serviceId);
			pstmt.setDate(2, java.sql.Date.valueOf(date));
			
			try (ResultSet rs = pstmt.executeQuery()) {
		        if (rs.next()) {
		            // Retrieve the timeslot_id from the result set
		        	info.put("timeslot_id", rs.getInt("timeslot_id"));
		        	info.put("service_name", rs.getString("service_name"));
		        	info.put("duration", rs.getInt("duration_in_hour"));
		            
		            System.out.println("Retrieved Timeslot ID: " + info.get("timeslot_id"));
		        } else {
		            System.out.println("No matching timeslot found.");
		        }
		        return info;
		    }
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		} 
	}

	private List<Integer> getTimeslotsByTimeslotId(int timeslot_id) throws SQLException {
		System.out.println("We are in the function to get the timeslots ");
		
		// Set Class
		try {
			Class.forName(dbClass);
		} catch(ClassNotFoundException e) {
			System.out.printf("Connection drive issue.", e);
			e.printStackTrace();
		}
				
		Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
		String[] slots = {"8am-9am", "9am-10am", "10am-11am", "11am-12pm", "1pm-2pm", "2pm-3pm", "3pm-4pm", "4pm-5pm", "5pm-6pm"};
		String query = """
						SELECT 
							"8am-9am", "9am-10am", "10am-11am", 
							"11am-12pm", "1pm-2pm", "2pm-3pm",
							"3pm-4pm", "4pm-5pm", "5pm-6pm"
						FROM 
							timeslot
						WHERE 
							timeslot_id = ?;
						""";
		List<Integer> timeslots = new ArrayList<>();
		
		try (PreparedStatement pstmt = conn.prepareStatement(query)) {
			pstmt.setInt(1, timeslot_id);
			
			try (ResultSet rs = pstmt.executeQuery()) {
		        if (rs.next()) {
		            // Loop the columns and add inside 'timeslots'
		        	for(String slot: slots) {
		        		timeslots.add(rs.getInt(slot)); // if it is null, will return 0
		        	}
		        } else {
		            System.out.println("No matching timeslot found.");
		        }
		        
		        return timeslots;
		    }
		}
		catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}












