package com.jc.wm.directory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import com.webmethods.sc.calendar.CalendarException;
import com.webmethods.sc.calendar.CalendarSystemFactory;
import com.webmethods.sc.calendar.Event;
import com.webmethods.sc.calendar.ICalendar;
import com.webmethods.sc.calendar.ICalendarManager;
import com.webmethods.sc.calendar.IWorkdayCalendar;
import com.webmethods.sc.calendar.PeriodEvent;
import com.webmethods.sc.calendar.Workday;
import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class Calendar {

	private ICalendarManager _cm;
	private String _datePattern = "yyyyMMdd hh:mm:ss";
	
	public Calendar() throws ServiceException {
				
		try {
			_cm = CalendarSystemFactory.getCalendarSystem().getCalendarManager();
		} catch (CalendarException e) {
			throw new ServiceException(e.toString());
		}
	}
	
	public void setDateFormatToUse(String datePattern) {
		_datePattern = datePattern;
	}
	
	public IData[] getAvailableCalendars(String location) {
		
		List<ICalendar> cals = _cm.listCalendars(location);
		
		List<IData> out = new ArrayList<IData>();
		
		cals.forEach((c) -> {
			out.add(makeCalRef(c));
		});
		
		return out.toArray(new IData[cals.size()]);
	}
	
	public IData[] getEventsForCalendar(String calendarId) throws ServiceException {
		
		ICalendar cal = _cm.getCalendarByID(calendarId);
		
		List<IData> out = new ArrayList();

		if (cal.getType() == ICalendar.TYPE_WORKDAY) {
			
			IWorkdayCalendar workCal = (IWorkdayCalendar) cal;
			
			List<Event> events = workCal.getEvents();
			
			events.forEach((e) -> {
				out.add(makeEventRef(workCal, e));
			});
		} else {
			throw new ServiceException("Calendar '" + calendarId + "' of type '" + cal.getType() + "' is not an event based calendar");
		}
		
		return out.toArray(new IData[out.size()]);
	}
	
	private IData makeCalRef(ICalendar cal) {
		
		IData out = IDataFactory.create();
		
		IDataCursor c = out.getCursor();
		IDataUtil.put(c, "id", cal.getID());
		IDataUtil.put(c, "alias", cal.getAlias());
		IDataUtil.put(c, "name", cal.getName());
		IDataUtil.put(c, "type", cal.getType());
		IDataUtil.put(c, "timezone", cal.getTimeZone());

		c.destroy();
		
		return out;
	}
	
	private IData makeEventRef(ICalendar cal, Event event) {
		
		IData out = IDataFactory.create();
		
		IDataCursor c = out.getCursor();
		IDataUtil.put(c, "name", event.getName());
		IDataUtil.put(c, "type", event.getType());
		
		if (event.getType() == Event.EVENT_TYPE_WORKDAY) {
			
			Workday workday = (Workday) event;
			
			IDataUtil.put(c, "day", workday.getDay());
			IDataUtil.put(c, "start", formatTimeAsString(workday.getStartHour(), workday.getStartMinute(), TimeZone.getTimeZone(cal.getTimeZone()).getRawOffset()));
			IDataUtil.put(c, "end", workday.getStartHour() + ":" + workday.getStartMinute());
		} else {
			
			PeriodEvent pe = (PeriodEvent) event;
			
			IDataUtil.put(c, "start", formatDateAsString(pe.getStartDate()));
			IDataUtil.put(c, "end", formatDateAsString(pe.getEndDate()));
		}

		c.destroy();
		
		return out;
	}
	
	private String formatTimeAsString(int hour, int minute, int GMTOffset) {
		
		java.util.Calendar calendar = GregorianCalendar.getInstance(); // creates a new calendar instance
			
		calendar.setTime(new Date());
		calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
		calendar.set(java.util.Calendar.MINUTE, hour);
		
		if (GMTOffset > 0)
			calendar.set(java.util.Calendar.ZONE_OFFSET, GMTOffset);

		return new SimpleDateFormat("HH:mm").format(calendar.getTime());
	}

	private String formatDateAsString(Date date) {
		
		return new SimpleDateFormat(_datePattern).format(date);
	}
}
