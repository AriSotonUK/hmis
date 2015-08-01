/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.divudi.bean.hr;

import com.divudi.bean.common.SessionController;
import com.divudi.bean.common.UtilityController;
import com.divudi.data.dataStructure.ShiftTable;
import com.divudi.data.hr.DayType;
import com.divudi.ejb.CommonFunctions;
import com.divudi.ejb.HumanResourceBean;
import com.divudi.entity.Staff;
import com.divudi.entity.hr.Roster;
import com.divudi.entity.hr.Shift;
import com.divudi.entity.hr.StaffShift;
import com.divudi.entity.hr.StaffShiftExtra;
import com.divudi.entity.hr.StaffShiftHistory;
import com.divudi.facade.StaffShiftFacade;
import com.divudi.facade.StaffShiftHistoryFacade;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.TemporalType;

/**
 *
 * @author safrin
 */
@Named
@SessionScoped
public class ShiftTableController implements Serializable {

    Date fromDate;
    Date toDate;
    Long dateRange;
    Roster roster;
    Shift shift;
    StaffShift staffShift;
    List<ShiftTable> shiftTables;
    @EJB
    HumanResourceBean humanResourceBean;
    @EJB
    CommonFunctions commonFunctions;
    @EJB
    StaffShiftFacade staffShiftFacade;
    @Inject
    SessionController sessionController;
    @Inject
    ShiftController shiftController;
    boolean all;
    Staff staff;

    //FUNTIONS
    public void makeNull() {
        fromDate = null;
        toDate = null;
        dateRange = 0l;
        roster = null;
        shiftTables = null;

    }

    private boolean errorCheck() {
        if (getFromDate() == null || getToDate() == null) {
            return true;
        }

        return false;
    }

    @Inject
    PhDateController phDateController;

    public void fetchAndSetDayType(StaffShift ss) {
        ss.setDayType(null);

        DayType dtp = phDateController.getHolidayType(ss.getShiftDate());
        ss.setDayType(dtp);
        if (ss.getDayType() == null) {
            if (ss.getShift() != null) {
                ss.setDayType(ss.getShift().getDayType());
            }
        }
    }

    private void saveStaffShift() {
        System.out.println("Save Shift 1");
        for (ShiftTable st : shiftTables) {
            for (StaffShift ss : st.getStaffShift()) {
//                if (ss.getShift() == null) {
//                    continue;
//                }

                fetchAndSetDayType(ss);
                ss.calShiftStartEndTime();
                ss.calLieu();
                if (ss.getId() == null) {
                    getStaffShiftFacade().create(ss);
                }

                ss.setPreviousStaffShift(getHumanResourceBean().calPrevStaffShift(ss));
                ss.setNextStaffShift(getHumanResourceBean().calFrwStaffShift(ss));
                getStaffShiftFacade().edit(ss);
            }
        }
    }

    @EJB
    StaffShiftHistoryFacade staffShiftHistoryFacade;

    private void saveHistory() {
        for (ShiftTable st : shiftTables) {
            for (StaffShift ss : st.getStaffShift()) {

                if (ss.getId() != null) {
                    System.out.println("ss.getId() = " + ss.getId());
                    boolean flag = false;
                    StaffShift fetchStaffShift = staffShiftFacade.find(ss.getId());
                    if (fetchStaffShift.getRoster() != null && ss.getRoster() != null) {
                        if (!fetchStaffShift.getRoster().equals(ss.getRoster())) {
                            System.out.println("Roster true");
                            flag = true;
                        }
                    }

                    if (fetchStaffShift.getStaff() != null && ss.getStaff() != null) {
                        if (!fetchStaffShift.getStaff().equals(ss.getStaff())) {
                            System.out.println("Staff True");
                            flag = true;
                        }
                    }
                    if (fetchStaffShift.getShift() != null && ss.getShift() != null) {
                        if (!fetchStaffShift.getShift().equals(ss.getShift())) {
                            System.out.println("Shift true");
                            System.out.println("fetchStaffShift.fetchStaffShift.getShift().getId() = " + fetchStaffShift.getShift().getId());
                            System.out.println("ss.getShift().getId() = " + ss.getShift().getId());
                            flag = true;
                        }
                    }

                    if (flag) {
                        System.out.println("Flag Inside Save Staff Shift History");
                        StaffShiftHistory staffShiftHistory = new StaffShiftHistory();
                        staffShiftHistory.setStaffShift(ss);
                        staffShiftHistory.setCreatedAt(new Date());
                        staffShiftHistory.setCreater(sessionController.getLoggedUser());
                        //CHanges
                        staffShiftHistory.setStaff(ss.getStaff());
                        staffShiftHistory.setShift(ss.getShift());
                        staffShiftHistory.setRoster(ss.getRoster());

                        staffShiftHistoryFacade.create(staffShiftHistory);
                    }
                }

            }
        }
    }

    public void save() {
        if (shiftTables == null) {
            return;
        }

        saveHistory();

        saveStaffShift();
        saveStaffShift();

    }

    public void createShiftTable() {
        if (errorCheck()) {
            return;
        }

        shiftTables = new ArrayList<>();

        Calendar nc = Calendar.getInstance();
        nc.setTime(getFromDate());
        Date nowDate = nc.getTime();

        nc.setTime(getToDate());
        nc.add(Calendar.DATE, 1);
        Date tmpToDate = nc.getTime();

        //CREATE FIRTS TABLE For Indexing Purpuse
        ShiftTable netT;

        while (tmpToDate.after(nowDate)) {
            netT = new ShiftTable();
            netT.setDate(nowDate);

            Calendar calNowDate = Calendar.getInstance();
            calNowDate.setTime(nowDate);

            Calendar calFromDate = Calendar.getInstance();
            calFromDate.setTime(getFromDate());

            if (calNowDate.get(Calendar.DATE) == calFromDate.get(Calendar.DATE)) {
                netT.setFlag(Boolean.TRUE);
            } else {
                netT.setFlag(Boolean.FALSE);
            }

            for (Staff stf : getHumanResourceBean().fetchStaff(getRoster())) {
                List<StaffShift> staffShifts = getHumanResourceBean().fetchStaffShift(nowDate, stf);
                if (staffShifts.isEmpty()) {
                    for (int i = getRoster().getShiftPerDay(); i > 0; i--) {
                        StaffShift ss = new StaffShift();
                        ss.setStaff(stf);
                        ss.setShiftDate(nowDate);
                        ss.setRoster(roster);
                        netT.getStaffShift().add(ss);
                    }
                } else {
                    for (StaffShift ss : staffShifts) {
                        netT.getStaffShift().add(ss);
                    }
                }

            }
            shiftTables.add(netT);

            Calendar c = Calendar.getInstance();
            c.setTime(nowDate);
            c.add(Calendar.DATE, 1);
            nowDate = c.getTime();

        }

        Long range = getCommonFunctions().getDayCount(getFromDate(), getToDate());
        setDateRange(range + 1);
    }

    public void fetchShiftTable() {
        if (errorCheck()) {
            return;
        }

        shiftTables = new ArrayList<>();

        Calendar nc = Calendar.getInstance();
        nc.setTime(getFromDate());
        Date nowDate = nc.getTime();

        nc.setTime(getToDate());
        nc.add(Calendar.DATE, 1);
        Date tmpToDate = nc.getTime();

        //CREATE FIRTS TABLE For Indexing Purpuse
        ShiftTable netT;

        while (tmpToDate.after(nowDate)) {
            netT = new ShiftTable();
            netT.setDate(nowDate);

            Calendar calNowDate = Calendar.getInstance();
            calNowDate.setTime(nowDate);

            Calendar calFromDate = Calendar.getInstance();
            calFromDate.setTime(getFromDate());

            if (calNowDate.get(Calendar.DATE) == calFromDate.get(Calendar.DATE)) {
                netT.setFlag(Boolean.TRUE);
            } else {
                netT.setFlag(Boolean.FALSE);
            }

//            List<StaffShift> staffShifts = getHumanResourceBean().fetchStaffShift(nowDate, roster);
//
//            for (StaffShift ss : staffShifts) {
//                netT.getStaffShift().add(ss);
//            }
            List<Staff> staffs = getHumanResourceBean().fetchStaffShift(fromDate, toDate, roster);

            for (Staff staff : staffs) {
                List<StaffShift> ss = getHumanResourceBean().fetchStaffShift(nowDate, staff);
                if (ss == null) {
                    for (int i = 0; i < roster.getShiftPerDay(); i++) {
                        StaffShift newStaffShift = new StaffShift();
                        newStaffShift.setStaff(staff);
                        newStaffShift.setShiftDate(nowDate);
                        newStaffShift.setCreatedAt(new Date());
                        newStaffShift.setCreater(sessionController.getLoggedUser());
                        netT.getStaffShift().add(newStaffShift);
                    }
                } else {
                    netT.getStaffShift().addAll(ss);
                    int ballance = roster.getShiftPerDay() - ss.size();
                    if (ballance < 0) {
                        continue;
                    }
                    for (int i = 0; i < ballance; i++) {
                        StaffShift newStaffShift = new StaffShift();
                        newStaffShift.setStaff(staff);
                        newStaffShift.setShiftDate(nowDate);
                        newStaffShift.setCreatedAt(new Date());
                        newStaffShift.setCreater(sessionController.getLoggedUser());
                        netT.getStaffShift().add(newStaffShift);
                    }

                }
            }

            shiftTables.add(netT);

            Calendar c = Calendar.getInstance();
            c.setTime(nowDate);
            c.add(Calendar.DATE, 1);
            nowDate = c.getTime();

        }

        Long range = getCommonFunctions().getDayCount(getFromDate(), getToDate());
        setDateRange(range + 1);
    }

    public void fetchShiftTableByStaff() {
        if (errorCheck()) {
            return;
        }
        if (staff == null) {
            UtilityController.addErrorMessage("Plaese Select Staff");
            return;
        }

        shiftTables = new ArrayList<>();

        Calendar nc = Calendar.getInstance();
        nc.setTime(getFromDate());
        Date nowDate = nc.getTime();

        nc.setTime(getToDate());
        nc.add(Calendar.DATE, 1);
        Date tmpToDate = nc.getTime();

        //CREATE FIRTS TABLE For Indexing Purpuse
        ShiftTable netT;

        while (tmpToDate.after(nowDate)) {
            netT = new ShiftTable();
            netT.setDate(nowDate);

            Calendar calNowDate = Calendar.getInstance();
            calNowDate.setTime(nowDate);

            Calendar calFromDate = Calendar.getInstance();
            calFromDate.setTime(getFromDate());

            if (calNowDate.get(Calendar.DATE) == calFromDate.get(Calendar.DATE)) {
                netT.setFlag(Boolean.TRUE);
            } else {
                netT.setFlag(Boolean.FALSE);
            }

//            List<StaffShift> staffShifts = getHumanResourceBean().fetchStaffShift(nowDate, roster);
//
//            for (StaffShift ss : staffShifts) {
//                netT.getStaffShift().add(ss);
//            }
            List<StaffShift> ss = getHumanResourceBean().fetchStaffShift(nowDate, staff);
            if (ss == null) {
                UtilityController.addErrorMessage("No Staff Shift");
                return;
            } else {
                netT.getStaffShift().addAll(ss);

            }

            shiftTables.add(netT);

            Calendar c = Calendar.getInstance();
            c.setTime(nowDate);
            c.add(Calendar.DATE, 1);
            nowDate = c.getTime();

        }

        Long range = getCommonFunctions().getDayCount(getFromDate(), getToDate());
        setDateRange(range + 1);
    }

    public void selectRosterLstener() {
        makeTableNull();
        getShiftController().setCurrentRoster(roster);

    }

    public void fetchShiftTableForCheck() {
        if (errorCheck()) {
            return;
        }

        shiftTables = new ArrayList<>();

        Calendar nc = Calendar.getInstance();
        nc.setTime(getFromDate());
        Date nowDate = nc.getTime();

        nc.setTime(getToDate());
        nc.add(Calendar.DATE, 1);
        Date tmpToDate = nc.getTime();

        //CREATE FIRTS TABLE For Indexing Purpuse
        ShiftTable netT;

        while (tmpToDate.after(nowDate)) {
            netT = new ShiftTable();
            netT.setDate(nowDate);

            Calendar calNowDate = Calendar.getInstance();
            calNowDate.setTime(nowDate);

            Calendar calFromDate = Calendar.getInstance();
            calFromDate.setTime(getFromDate());

            if (calNowDate.get(Calendar.DATE) == calFromDate.get(Calendar.DATE)) {
                netT.setFlag(Boolean.TRUE);
            } else {
                netT.setFlag(Boolean.FALSE);
            }

//            List<StaffShift> staffShifts = getHumanResourceBean().fetchStaffShift(nowDate, roster);
//
//            for (StaffShift ss : staffShifts) {
//                netT.getStaffShift().add(ss);
//            }
            List<Staff> staffs = getHumanResourceBean().fetchStaffShift(fromDate, toDate, roster);

            for (Staff staff : staffs) {
                List<StaffShift> ss = getHumanResourceBean().fetchStaffShift(nowDate, staff);
                if (ss == null) {
                    for (int i = 0; i < roster.getShiftPerDay(); i++) {
                        StaffShift newStaffShift = new StaffShift();
                        newStaffShift.setStaff(staff);
                        newStaffShift.setShiftDate(nowDate);
                        newStaffShift.setCreatedAt(new Date());
                        newStaffShift.setCreater(sessionController.getLoggedUser());
                        newStaffShift.setTransWorkTime(0.0);
                        netT.getStaffShift().add(newStaffShift);
                    }
                } else {
                    for (StaffShift s : ss) {
                        System.out.println("s.getShift().getDurationMin() = " + s.getShift().getDurationMin());
                        if (s.getShift().getDurationMin()>0) {
                            s.setTransWorkTime(fetchWorkTime(staff, nowDate));
                            System.out.println("fetchWorkTime(staff, nowDate) = " + fetchWorkTime(staff, nowDate));
                        }
                    }
                    netT.getStaffShift().addAll(ss);
                    int ballance = roster.getShiftPerDay() - ss.size();
                    if (ballance < 0) {
                        continue;
                    }
                    for (int i = 0; i < ballance; i++) {
                        StaffShift newStaffShift = new StaffShift();
                        newStaffShift.setStaff(staff);
                        newStaffShift.setShiftDate(nowDate);
                        newStaffShift.setCreatedAt(new Date());
                        newStaffShift.setCreater(sessionController.getLoggedUser());
                        netT.getStaffShift().add(newStaffShift);
                    }

                }
            }

            shiftTables.add(netT);

            Calendar c = Calendar.getInstance();
            c.setTime(nowDate);
            c.add(Calendar.DATE, 1);
            nowDate = c.getTime();

        }

        Long range = getCommonFunctions().getDayCount(getFromDate(), getToDate());
        setDateRange(range + 1);
    }

    public double fetchWorkTime(Staff staff,Date date) {

        Object[] obj = fetchWorkedTimeByDateOnly(staff,date);

        System.err.println("list = " + obj);

        Double value = (Double) obj[0] != null ? (Double) obj[0] : 0;
        Double valueExtra = (Double) obj[1] != null ? (Double) obj[1] : 0;
        Double totalExtraDuty = (Double) obj[2] != null ? (Double) obj[2] : 0;
        StaffShift ss = (StaffShift) obj[3] != null ? (StaffShift) obj[3] : new StaffShift();
        Double leavedTimeValue = (Double) obj[4] != null ? (Double) obj[4] : 0;

        System.err.println("Staff " + staff.getCodeInterger() + " :Value : " + value);
        if (ss.getShift() != null && ss.getShift().getLeaveHourHalf() != 0 && leavedTimeValue > 0) {
            System.out.println("value = " + value);
            System.out.println("leavedTimeValue = " + leavedTimeValue);
            System.out.println("ss.getShift().getDurationMin()*60 = " + ss.getShift().getDurationMin() * 60);
            if ((ss.getShift().getDurationMin() * 60) < value) {
                value = ss.getShift().getDurationMin() * 60;
                System.out.println("4.b dbl(else) = " + value);
            }
        }

        System.err.println("Staff " + staff.getCodeInterger() + " : Value : " + value);
        
        return value;

    }

    private Object[] fetchWorkedTimeByDateOnly(Staff staff,Date date) {
        String sql = "";

        HashMap hm = new HashMap();
        sql = "select "
                + " sum(ss.workedWithinTimeFrameVarified+ss.leavedTime),"
                + " sum(ss.extraTimeFromStartRecordVarified+ss.extraTimeFromEndRecordVarified),"
                + " sum((ss.extraTimeFromStartRecordVarified+ss.extraTimeFromEndRecordVarified)*ss.multiplyingFactorOverTime*ss.overTimeValuePerSecond), "
                + " ss, "
                + " sum(ss.leavedTime)"
                + " from StaffShift ss "
                + " where ss.retired=false "
                + " and type(ss)!=:tp"
                + " and ss.staff=:stf"
                //                + " and ss.leavedTime=0 "
                + " and ss.dayType not in :dtp "
                //                + " and ((ss.startRecord.recordTimeStamp is not null "
                //                + " and ss.endRecord.recordTimeStamp is not null) "
                //                + " or (ss.leaveType is not null) ) "
                + " and ss.shiftDate=:date ";
        hm.put("date", date);
        hm.put("tp", StaffShiftExtra.class);
        hm.put("stf", staff);
        hm.put("dtp", Arrays.asList(new DayType[]{DayType.DayOff, DayType.MurchantileHoliday, DayType.SleepingDay, DayType.Poya}));

        if (staff != null) {
            sql += " and ss.staff=:stf ";
            hm.put("stf", staff);
        }

        sql += " order by ss.dayOfWeek,ss.staff.codeInterger ";
        return staffShiftFacade.findAggregate(sql, hm, TemporalType.TIMESTAMP);
    }

    public void makeTableNull() {
        shiftTables = null;
    }

    //GETTER AND SETTERS
    public ShiftController getShiftController() {
        return shiftController;
    }

    public void setShiftController(ShiftController shiftController) {
        this.shiftController = shiftController;
    }

    public CommonFunctions getCommonFunctions() {
        return commonFunctions;
    }

    public void setCommonFunctions(CommonFunctions commonFunctions) {
        this.commonFunctions = commonFunctions;
    }

    public SessionController getSessionController() {
        return sessionController;
    }

    public void setSessionController(SessionController sessionController) {
        this.sessionController = sessionController;
    }

    public HumanResourceBean getHumanResourceBean() {
        return humanResourceBean;
    }

    public void setHumanResourceBean(HumanResourceBean humanResourceBean) {
        this.humanResourceBean = humanResourceBean;
    }

    public Roster getRoster() {
        return roster;
    }

    public void setRoster(Roster roster) {
        this.roster = roster;
    }

    public Long getDateRange() {
        return dateRange;
    }

    public void setDateRange(Long dateRange) {
        this.dateRange = dateRange;
    }

    public List<ShiftTable> getShiftTables() {
        return shiftTables;
    }

    public void setShiftTables(List<ShiftTable> shiftTables) {
        this.shiftTables = shiftTables;
    }

    public Date getFromDate() {
        return fromDate;
    }

    public void setFromDate(Date fromDate) {
        this.fromDate = fromDate;
    }

    public Date getToDate() {
        return toDate;
    }

    public void setToDate(Date toDate) {
        this.toDate = toDate;
    }

    public StaffShiftFacade getStaffShiftFacade() {
        return staffShiftFacade;
    }

    public void setStaffShiftFacade(StaffShiftFacade staffShiftFacade) {
        this.staffShiftFacade = staffShiftFacade;
    }

    public void visible() {
        all = true;
    }

    public void hide() {
        all = false;
    }

    public boolean isAll() {
        return all;
    }

    public void setAll(boolean all) {
        this.all = all;
    }

    public StaffShiftHistoryFacade getStaffShiftHistoryFacade() {
        return staffShiftHistoryFacade;
    }

    public void setStaffShiftHistoryFacade(StaffShiftHistoryFacade staffShiftHistoryFacade) {
        this.staffShiftHistoryFacade = staffShiftHistoryFacade;
    }

    public Shift getShift() {
        return shift;
    }

    public void setShift(Shift shift) {
        this.shift = shift;
    }

    public StaffShift getStaffShift() {
        return staffShift;
    }

    public void setStaffShift(StaffShift staffShift) {
        this.staffShift = staffShift;
    }

    public Staff getStaff() {
        return staff;
    }

    public void setStaff(Staff staff) {
        this.staff = staff;
    }

}
