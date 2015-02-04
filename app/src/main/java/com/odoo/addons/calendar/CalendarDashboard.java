/**
 * Odoo, Open Source Management Solution
 * Copyright (C) 2012-today Odoo SA (<http:www.odoo.com>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 *
 * Created on 9/1/15 10:34 AM
 */
package com.odoo.addons.calendar;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import com.odoo.addons.calendar.models.CalendarEvent;
import com.odoo.addons.calendar.utils.TodayIcon;
import com.odoo.addons.crm.CRMDetail;
import com.odoo.addons.crm.models.CRMLead;
import com.odoo.addons.phonecall.PhoneCallDetail;
import com.odoo.addons.phonecall.models.CRMPhoneCalls;
import com.odoo.base.addons.res.ResPartner;
import com.odoo.calendar.SysCal;
import com.odoo.calendar.view.OdooCalendar;
import com.odoo.core.orm.ODataRow;
import com.odoo.core.orm.OValues;
import com.odoo.core.orm.fields.OColumn;
import com.odoo.core.support.addons.fragment.BaseFragment;
import com.odoo.core.support.addons.fragment.IOnSearchViewChangeListener;
import com.odoo.core.support.addons.fragment.ISyncStatusObserverListener;
import com.odoo.core.support.drawer.ODrawerItem;
import com.odoo.core.support.list.IOnItemClickListener;
import com.odoo.core.support.list.OCursorListAdapter;
import com.odoo.core.support.list.OListAdapter;
import com.odoo.core.utils.IntentUtils;
import com.odoo.core.utils.OControls;
import com.odoo.core.utils.OCursorUtils;
import com.odoo.core.utils.ODateUtils;
import com.odoo.core.utils.OResource;
import com.odoo.core.utils.StringUtils;
import com.odoo.core.utils.sys.IOnBackPressListener;
import com.odoo.crm.R;
import com.odoo.widgets.bottomsheet.BottomSheet;
import com.odoo.widgets.bottomsheet.BottomSheetListeners;
import com.odoo.widgets.snackbar.SnackBar;
import com.odoo.widgets.snackbar.SnackbarBuilder;
import com.odoo.widgets.snackbar.listeners.ActionClickListener;
import com.odoo.widgets.snackbar.listeners.EventListener;

import java.util.ArrayList;
import java.util.List;

public class CalendarDashboard extends BaseFragment implements View.OnClickListener,
        BottomSheetListeners.OnSheetItemClickListener, IOnBackPressListener,
        OdooCalendar.OdooCalendarDateSelectListener, LoaderManager.LoaderCallbacks<Cursor>,
        OCursorListAdapter.OnViewBindListener, SwipeRefreshLayout.OnRefreshListener,
        ISyncStatusObserverListener, BottomSheetListeners.OnSheetActionClickListener,
        BottomSheetListeners.OnSheetMenuCreateListener, EventListener,
        IOnSearchViewChangeListener, IOnItemClickListener, OCursorListAdapter.OnViewCreateListener, AdapterView.OnItemSelectedListener {
    public static final String TAG = CalendarDashboard.class.getSimpleName();
    public static final String KEY = "key_calendar_dashboard";
    public static final String KEY_DATE = "key_date";
    private BottomSheet mSheet = null;
    private OdooCalendar odooCalendar;
    private View calendarView = null;
    private ListView dashboardListView;
    private View mView;
    private SysCal.DateInfo mDateInfo = null;
    private String mFilterDate;
    private OCursorListAdapter mAdapter;
    private boolean syncRequested = false;
    private String mFilter = null;
    private String wonLost = "won";
    private CRMLead crmLead;
    private ODataRow convertRequestRecord;
    private Spinner navSpinner;
    private OListAdapter navSpinnerAdapter;
    private FilterType mFilterType = FilterType.All;
    private String date_start = "false";
    private String date_end = "false";

    private enum SheetType {
        Event, PhoneCall, Opportunity
    }

    private enum FilterType {
        All(R.string.label_all), Meetings(R.string.label_meetings),
        Opportunities(R.string.label_opportunity), PhoneCalls(R.string.label_phone_calls);
        int str_id = 0;

        FilterType(int type) {
            str_id = type;
        }

        public String getString(Context context) {
            return OResource.string(context, str_id);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        return inflater.inflate(R.layout.calendar_dashboard, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mView = view;
        setHasFloatingButton(view, R.id.fabButton, null, this);
        parent().setOnBackPressListener(this);
        parent().setHasActionBarSpinner(true);
        navSpinner = parent().getActionBarSpinner();
        initActionSpinner();
        odooCalendar = (OdooCalendar) view.findViewById(R.id.dashboard_calendar);
        crmLead = new CRMLead(getActivity(), null);
        odooCalendar.setOdooCalendarDateSelectListener(this);
    }

    private void initActionSpinner() {
        List<Object> spinnerItems = new ArrayList<>();
        ODataRow row = new ODataRow();
        row.put("key", FilterType.All.toString());
        row.put("name", FilterType.All.getString(getActivity()));
        spinnerItems.add(row);
        row = new ODataRow();
        row.put("key", FilterType.Meetings.toString());
        row.put("name", FilterType.Meetings.getString(getActivity()));
        spinnerItems.add(row);
        row = new ODataRow();
        row.put("key", FilterType.Opportunities.toString());
        row.put("name", FilterType.Opportunities.getString(getActivity()));
        spinnerItems.add(row);
        row = new ODataRow();
        row.put("key", FilterType.PhoneCalls.toString());
        row.put("name", FilterType.PhoneCalls.getString(getActivity()));
        spinnerItems.add(row);
        navSpinnerAdapter = new OListAdapter(getActivity(), R.layout.base_simple_list_item_1, spinnerItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getActivity()).inflate(R.layout.base_simple_list_item_1_selected
                            , parent, false);
                }
                return getSpinnerView(getItem(position), position, convertView, parent);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getActivity()).inflate(getResource(), parent, false);
                }
                return getSpinnerView(getItem(position), position, convertView, parent);
            }
        };
        navSpinner.setAdapter(navSpinnerAdapter);
        navSpinner.setOnItemSelectedListener(this);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        ODataRow row = (ODataRow) navSpinnerAdapter.getItem(position);
        mFilterType = FilterType.valueOf(row.getString("key"));
        if (mFilterDate != null)
            getLoaderManager().restartLoader(0, null, this);
    }

    private View getSpinnerView(Object row, int pos, View view, ViewGroup parent) {
        ODataRow r = (ODataRow) row;
        OControls.setText(view, android.R.id.text1, r.getString("name"));
        return view;
    }

    @Override
    public List<OdooCalendar.DateDataObject> weekDataInfo(
            List<SysCal.DateInfo> week_dates) {
        List<OdooCalendar.DateDataObject> items = new ArrayList<>();
        CalendarEvent event = (CalendarEvent) db();
        CRMPhoneCalls calls = new CRMPhoneCalls(getActivity(), event.getUser());
        CRMLead lead = new CRMLead(getActivity(), event.getUser());
        for (SysCal.DateInfo date : week_dates) {
            String date_str = date.getDateString();
            int total = 0;
            // Checking for events
            total += event.countGroupBy("date_start", "date(date_start)"
                    , "date(date_start) = ?", new String[]{date_str}).getInt("total");
            total += event.countGroupBy("date_end", "date(date_end)"
                    , "date(date_end) = ?", new String[]{date_str}).getInt("total");

            // Checking for phone calls
            total += calls.countGroupBy("date", "date(date)", "date(date) = ?", new String[]{date_str})
                    .getInt("total");

            // Leads
            total += lead.countGroupBy("date_deadline", "date(date_deadline)",
                    "date(date_deadline) = ?", new String[]{date_str}).getInt("total");
            total += lead.countGroupBy("date_deadline", "date(date_deadline)",
                    "date(date_deadline) = ?", new String[]{date_str}).getInt("total");
            items.add(new OdooCalendar.DateDataObject(date_str, (total > 0)));
        }
        return items;
    }

    @Override
    public View getEventsView(ViewGroup parent, SysCal.DateInfo date) {
        calendarView = LayoutInflater.from(getActivity()).inflate(
                R.layout.calendar_dashboard_items, parent, false);
        calendarView.findViewById(R.id.dashboard_no_item_view)
                .setOnClickListener(this);
        dashboardListView = (ListView) calendarView
                .findViewById(R.id.items_container);
        setHasFloatingButton(mView, R.id.fabButton, dashboardListView, this);
        mDateInfo = date;
        initAdapter();
        mFilterDate = ODateUtils.convertToUTC(date.getYear() + "-" +
                date.getMonth() + "-" + date.getDate() + " 00:00:00", ODateUtils.DEFAULT_FORMAT);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                getLoaderManager().restartLoader(0, null, CalendarDashboard.this);
            }
        }, 500);
        return calendarView;
    }

    private void initAdapter() {
        mAdapter = new OCursorListAdapter(getActivity(), null,
                R.layout.calendar_dashboard_item_view);
        mAdapter.setOnViewBindListener(this);
        mAdapter.setOnViewCreateListener(this);
        dashboardListView.setAdapter(mAdapter);
        mAdapter.changeCursor(null);
        mAdapter.handleItemClickListener(dashboardListView, this);
        setHasSyncStatusObserver(KEY, this, db());
    }


    @Override
    public void onItemDoubleClick(View view, int position) {
        ODataRow row = OCursorUtils.toDatarow((Cursor) mAdapter.getItem(position));
        String type = row.getString("data_type");
        Class<?> cls = EventDetail.class;
        if (type.equals("phone_call")) {
            cls = PhoneCallDetail.class;
        }
        if (type.equals("opportunity")) {
            cls = CRMDetail.class;
        }
        IntentUtils.startActivity(getActivity(), cls, row.getPrimaryBundleData());
    }

    @Override
    public void onItemClick(View view, int position) {
        Cursor cr = (Cursor) mAdapter.getItem(position);
        String data_type = cr.getString(cr.getColumnIndex("data_type"));
        if (data_type.equals("event")) {
            showSheet(SheetType.Event, cr);
        }
        if (data_type.equals("phone_call")) {
            showSheet(SheetType.PhoneCall, cr);
        }
        if (data_type.equals("opportunity")) {
            showSheet(SheetType.Opportunity, cr);
        }
    }

    private void showSheet(SheetType type, Cursor data) {
        if (mSheet != null) {
            mSheet.dismiss();
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(getActivity());
        builder.listener(this);
        builder.setIconColor(_c(R.color.theme_primary_dark));
        builder.setTextColor(Color.parseColor("#414141"));
        builder.setData(data);
        builder.actionListener(this);
        builder.setActionIcon(R.drawable.ic_action_edit);
        builder.title(data.getString(data.getColumnIndex("name")));
        builder.setOnSheetMenuCreateListener(this);
        switch (type) {
            case Event:
                builder.menu(R.menu.menu_dashboard_events);
                break;
            case PhoneCall:
                builder.menu(R.menu.menu_dashboard_phonecalls);
                break;
            case Opportunity:
                builder.menu(R.menu.menu_dashboard_opportunity);
                break;
        }
        mSheet = builder.create();
        mSheet.show();
    }

    @Override
    public void onSheetActionClick(BottomSheet sheet, final Object extras) {
        sheet.dismiss();
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                Cursor cr = (Cursor) extras;
                String data_type = cr.getString(cr.getColumnIndex("data_type"));
                int record_id = cr.getInt(cr.getColumnIndex(OColumn.ROW_ID));
                if (data_type.equals("phone_call")) {
                    Bundle extra = new Bundle();
                    extra.putInt(OColumn.ROW_ID, record_id);
                    IntentUtils.startActivity(getActivity(), PhoneCallDetail.class, extra);
                }
                if (data_type.equals("event")) {
                    Bundle extra = new Bundle();
                    extra.putInt(OColumn.ROW_ID, record_id);
                    IntentUtils.startActivity(getActivity(), EventDetail.class, extra);
                }

                if (data_type.equals("opportunity")) {
                    Bundle bundle = new Bundle();
                    bundle.putInt(OColumn.ROW_ID, record_id);
                    IntentUtils.startActivity(getActivity(), CRMDetail.class, bundle);
                }
            }
        }, 250);

    }

    @Override
    public void onSheetMenuCreate(Menu menu, Object extras) {
        Cursor cr = (Cursor) extras;
        String type = cr.getString(cr.getColumnIndex("data_type"));
        String is_done = cr.getString(cr.getColumnIndex("is_done"));
        if (is_done.equals("0"))
            return;
        MenuItem mark_done = null;
        if (type.equals("event")) {
            mark_done = menu.findItem(R.id.menu_events_all_done);
        }
        if (type.equals("phone_call")) {
            mark_done = menu.findItem(R.id.menu_phonecall_all_done);
        }
        if (type.equals("opportunity")) {
        }
        if (mark_done != null) {
            mark_done.setTitle("Mark Undone");
            mark_done.setIcon(R.drawable.ic_action_mark_undone);
        }
    }

    @Override
    public View onViewCreated(Context context, ViewGroup view, Cursor cr, int position) {
        String data_type = cr.getString(cr.getColumnIndex("data_type"));
        if (data_type.equals("separator")) {
            return LayoutInflater.from(getActivity()).inflate(
                    R.layout.calendar_dashboard_item_separator, view, false);
        }
        return LayoutInflater.from(getActivity()).inflate(
                R.layout.calendar_dashboard_item_view, view, false);
    }

    @Override
    public void onViewBind(View view, Cursor cursor, ODataRow row) {
        String type = row.getString("data_type");
        int icon = -1;
        if (type.equals("separator")) {
            OControls.setText(view, R.id.list_separator, row.getString("name"));
        } else {
            String date = "false";

            date_start = row.getString("date_start");
            date_end = row.getString("date_end");
            if (!date_start.equals("false")) {
                date_start = ODateUtils.convertToDefault(row.getString("date_start"), ODateUtils.DEFAULT_FORMAT, "dd-MM-yyyy");
            }
            if (!date_end.equals("false")) {
                date_end = ODateUtils.convertToDefault(row.getString("date_end"), ODateUtils.DEFAULT_FORMAT, "dd-MM-yyyy");
            }

            if (row.getString("description").equals("false")) {
                row.put("description", "");
            }

            if (type.equals("event")) {
                icon = R.drawable.ic_action_event;
                if (row.getString("allday").equals("false")) {
                    date = row.getString("date_start");
                    view.findViewById(R.id.allDay).setVisibility(View.GONE);
                } else {
                    view.findViewById(R.id.allDay).setVisibility(View.VISIBLE);
                }
            }

            if (type.equals("phone_call")) {
                icon = R.drawable.ic_action_phone;
                date = row.getString("date");
            }
            if (type.equals("opportunity")) {
                icon = R.drawable.ic_action_opportunities;
            }

            if (!date.equals("false")) {
                date = ODateUtils.convertToDefault(date, ODateUtils.DEFAULT_FORMAT, "hh:mm a");
                OControls.setText(view, R.id.event_time, date);
            }
            OControls.setText(view, R.id.event_description, row.getString("description"));
            Boolean is_done = row.getString("is_done").equals("1");
            OControls.setImage(view, R.id.event_icon, icon);
            if (is_done) {
                int title_color = (is_done) ? Color.LTGRAY : Color.parseColor("#414141");
                int time_color = (is_done) ? Color.LTGRAY : _c(R.color.theme_secondary_light);
                int desc_color = (is_done) ? Color.LTGRAY : Color.parseColor("#aaaaaa");
                int allDay_color = (is_done) ? Color.LTGRAY : _c(R.color.theme_secondary);
                view.findViewById(R.id.event_icon).setBackgroundResource(
                        R.drawable.circle_mask_gray);
                OControls.setTextColor(view, R.id.event_name, title_color);
                OControls.setTextColor(view, R.id.event_time, time_color);
                OControls.setTextColor(view, R.id.event_description, desc_color);
                OControls.setTextColor(view, R.id.allDay, allDay_color);
                OControls.setTextViewStrikeThrough(view, R.id.event_name);
                OControls.setTextViewStrikeThrough(view, R.id.event_time);
                OControls.setTextViewStrikeThrough(view, R.id.event_description);
                OControls.setTextViewStrikeThrough(view, R.id.allDay);
            }
            OControls.setText(view, R.id.event_name, row.getString("name"));
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle data) {
        List<String> args = new ArrayList<>();
        String where = "";
        String date_end = ODateUtils.getDateDayBeforeAfterUTC(mFilterDate, 1);
        args.add(mFilterDate);
        CalendarEvent event = (CalendarEvent) db();
        Uri uri = event.agendaUri();
        switch (mFilterType) {
            case PhoneCalls:
                CRMPhoneCalls phoneCalls = new CRMPhoneCalls(getActivity(), db().getUser());
                uri = phoneCalls.uri();
                where = "date(date) >=  ? and date(date) <= ? and state = ?";
                args.add(date_end);
                args.add("open");
                if (mFilter != null) {
                    where += " and (name like ? or description like ?)";
                    args.add("%" + mFilter + "%");
                }
                break;
            case Opportunities:
                CRMLead leads = new CRMLead(getActivity(), db().getUser());
                uri = leads.uri();
                where = "(date(date_deadline) >= ? and date(date_deadline) <= ? or date(date_action) >= ? and date(date_action) <= ?) and type = ?";
                args.add(date_end);
                args.add(mFilterDate);
                args.add(date_end);
                args.add("opportunity");
                if (mFilter != null) {
                    where += " and (name like ? or description like ?)";
                    args.add("%" + mFilter + "%");
                }
                break;
            case Meetings:
                uri = db().uri();
                where = "(date(date_start) BETWEEN ? AND ? OR date(date_end) BETWEEN ? AND ?)";
                args.add(date_end);
                args.add(mFilterDate);
                args.add(date_end);
                if (mFilter != null) {
                    where += " and name like ?";
                }
                break;
        }
        if (mFilter != null) {
            args.add("%" + mFilter + "%");
        }
        return new CursorLoader(getActivity(), uri,
                null, where, args.toArray(new String[args.size()]), null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, final Cursor cr) {
        mAdapter.changeCursor(cr);
        OControls.setVisible(calendarView, R.id.dashboard_progress);
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                if (cr.getCount() > 0) {
                    OControls.setGone(calendarView, R.id.dashboard_progress);
                    OControls.setVisible(calendarView, R.id.items_container);
                    OControls.setGone(calendarView, R.id.dashboard_no_items);
                    setHasSwipeRefreshView(calendarView, R.id.swipe_container,
                            CalendarDashboard.this);
                } else {
                    setHasSwipeRefreshView(calendarView,
                            R.id.dashboard_no_items, CalendarDashboard.this);
                    if (db().isEmptyTable() && !syncRequested) {
                        syncRequested = true;
                        parent().sync().requestSync(
                                CalendarEvent.AUTHORITY);
                        setSwipeRefreshing(true);
                    }
                    OControls.setGone(calendarView, R.id.dashboard_progress);
                    OControls.setVisible(calendarView, R.id.dashboard_no_items);
                }
            }
        }, 300);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.changeCursor(null);
    }

    @Override
    public List<ODrawerItem> drawerMenus(Context context) {
        List<ODrawerItem> menu = new ArrayList<>();
        menu.add(new ODrawerItem(KEY).setTitle("Agenda")
                .setInstance(new CalendarDashboard())
                .setIcon(R.drawable.ic_action_dashboard));
        return menu;
    }

    @Override
    public Class<CalendarEvent> database() {
        return CalendarEvent.class;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fabButton:
                onFabClick();
                break;
            case R.id.dashboard_no_item_view:
                createEvent();
                break;
        }
    }

    private void onFabClick() {
        BottomSheet.Builder builder = new BottomSheet.Builder(getActivity());
        builder.listener(this);
        builder.setIconColor(_c(R.color.theme_secondary_dark));
        builder.setTextColor(Color.parseColor("#414141"));
        ODataRow data = new ODataRow();
        data.put("fab", true);
        builder.setData(data);
        builder.title(_s(R.string.label_new));
        builder.menu(R.menu.menu_dashboard_fab);
        mSheet = builder.create();
        mSheet.show();
    }

    @Override
    public void onItemClick(BottomSheet sheet, MenuItem menu, Object extras) {
        dismissSheet(sheet);
        if (extras instanceof ODataRow) {
            onFabMenuClick(menu);
            return;
        }
        actionEvent(menu, (Cursor) extras);
    }

    private void actionEvent(MenuItem menu, Cursor cr) {
        String is_done = cr.getString(cr.getColumnIndex("is_done"));
        final OValues values = new OValues();
        values.put("_is_dirty", "false"); // to ignore update on server
        final int row_id = cr.getInt(cr.getColumnIndex(OColumn.ROW_ID));
        values.put("is_done", (is_done.equals("0")) ? 1 : 0);
        String done_label = (is_done.equals("0")) ? "done" : "undone";
        ODataRow row = OCursorUtils.toDatarow(cr);
        convertRequestRecord = row;
        Bundle data = row.getPrimaryBundleData();
        switch (menu.getItemId()) {
            // Event menus
            case R.id.menu_events_location:
                String location = cr.getString(cr.getColumnIndex("location"));
                if (location.equals("false")) {
                    Toast.makeText(getActivity(), _s(R.string.label_no_location_found),
                            Toast.LENGTH_LONG).show();
                } else {
                    IntentUtils.redirectToMap(getActivity(), location);
                }
                break;
            case R.id.menu_events_reschedule:
                data.putBoolean(EventDetail.KEY_RESCHEDULE, true);
                IntentUtils.startActivity(getActivity(), EventDetail.class, data);
                break;
            // Opportunity menus
            case R.id.menu_opp_customer_location:
                String address = cr.getString(cr.getColumnIndex("street")) + " ";
                address += cr.getString(cr.getColumnIndex("street2")) + " ";
                address += cr.getString(cr.getColumnIndex("city")) + " ";
                address += cr.getString(cr.getColumnIndex("zip"));
                address = address.replaceAll("false", "");
                if (TextUtils.isEmpty(address.trim())) {
                    Toast.makeText(getActivity(), _s(R.string.label_no_location_found),
                            Toast.LENGTH_LONG).show();
                } else {
                    IntentUtils.redirectToMap(getActivity(), address);
                }
                break;
            case R.id.menu_opp_call_customer:
            case R.id.menu_phonecall_call:
                int partner_id = cr.getInt(cr.getColumnIndex("partner_id"));
                if (partner_id != 0) {
                    String contact = ResPartner.getContact(getActivity(), partner_id);
                    if (contact != null && !contact.equals("false")) {
                        IntentUtils.requestCall(getActivity(), contact);
                    } else {
                        Toast.makeText(getActivity(), _s(R.string.label_no_contact_found),
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(getActivity(), _s(R.string.label_no_contact_found),
                            Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.menu_opp_lost:
                if (inNetwork()) {
                    wonLost = "lost";
                    crmLead.markWonLost(wonLost, row, markDoneListener);
                } else {
                    Toast.makeText(getActivity(), _s(R.string.toast_network_required), Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.menu_opp_won:
                if (inNetwork()) {
                    wonLost = "won";
                    crmLead.markWonLost(wonLost, row, markDoneListener);
                } else {
                    Toast.makeText(getActivity(), _s(R.string.toast_network_required), Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.menu_opp_reschedule:
                IntentUtils.startActivity(getActivity(), CRMDetail.class, row.getPrimaryBundleData());
                break;

            case R.id.menu_phonecall_reschedule:
                row = OCursorUtils.toDatarow(cr);
                IntentUtils.startActivity(getActivity(), PhoneCallDetail.class, row.getPrimaryBundleData());
                break;
            // All done menu
            case R.id.menu_phonecall_all_done:
                final CRMPhoneCalls phone_call = new CRMPhoneCalls(getActivity(), null);
                phone_call.update(row_id, values);
                getLoaderManager().restartLoader(0, null, this);
                SnackBar.get(getActivity()).text(_s(R.string.label_event_marked) + done_label)
                        .actionColor(_c(R.color.theme_primary_light))
                        .duration(SnackbarBuilder.SnackbarDuration.LENGTH_LONG)
                        .withAction("undo", new ActionClickListener() {

                            @Override
                            public void onActionClicked(SnackbarBuilder snackbar) {
                                values.put("is_done", (values.getString("is_done")
                                        .equals("0")) ? 1 : 0);
                                phone_call.update(row_id, values);
                                getLoaderManager().restartLoader(0, null,
                                        CalendarDashboard.this);
                            }
                        }).withEventListener(this).show();
                break;
            case R.id.menu_events_all_done:
                db().update(row_id, values);
                getLoaderManager().restartLoader(0, null, this);
                SnackBar.get(getActivity()).text(_s(R.string.label_event_marked) + done_label)
                        .actionColor(_c(R.color.theme_primary_light))
                        .duration(SnackbarBuilder.SnackbarDuration.LENGTH_LONG)
                        .withAction("undo", new ActionClickListener() {

                            @Override
                            public void onActionClicked(SnackbarBuilder snackbar) {
                                values.put("is_done", (values.getString("is_done")
                                        .equals("0")) ? 1 : 0);
                                db().update(row_id, values);
                                getLoaderManager().restartLoader(0, null,
                                        CalendarDashboard.this);
                            }
                        }).withEventListener(this).show();
                break;
        }
    }

    CRMLead.OnOperationSuccessListener markDoneListener = new CRMLead.OnOperationSuccessListener() {
        @Override
        public void OnSuccess() {
            Toast.makeText(getActivity(), StringUtils.capitalizeString(convertRequestRecord.getString("type"))
                    + _s(R.string.toast_marked) + wonLost, Toast.LENGTH_LONG).show();
        }

        @Override
        public void OnCancelled() {

        }
    };

    private void onFabMenuClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_fab_new_event:
                createEvent();
                break;
            case R.id.menu_fab_new_call_log:
                IntentUtils.startActivity(getActivity(), PhoneCallDetail.class, null);
                break;
        }
    }

    private void dismissSheet(final BottomSheet sheet) {
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                sheet.dismiss();
            }
        }, 100);
    }

    @Override
    public boolean onBackPressed() {
        if (mSheet != null && mSheet.isShowing()) {
            mSheet.dismiss();
            return false;
        }
        return true;
    }


    @Override
    public void onRefresh() {
        if (inNetwork()) {
            parent().sync().requestSync(CalendarEvent.AUTHORITY);
            setSwipeRefreshing(true);
        } else {
            hideRefreshingProgress();
            Toast.makeText(getActivity(),
                    _s(R.string.toast_network_required), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStatusChange(Boolean refreshing) {
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onShow(int height) {
        hideFab();
    }

    @Override
    public void onDismiss(int height) {
        showFab();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_calendar_dashboard, menu);
        if (getActivity() != null) {
            MenuItem today = menu.findItem(R.id.menu_dashboard_goto_today);
            today.setIcon(TodayIcon.get(getActivity()).getIcon());
        }
        setHasSearchView(this, menu, R.id.menu_search);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_dashboard_goto_today:
                odooCalendar.goToToday();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSearchViewTextChange(String newFilter) {
        mFilter = newFilter;
        getLoaderManager().restartLoader(0, null, this);
        return true;
    }

    @Override
    public void onSearchViewClose() {
        // Nothing to do
    }

    private void createEvent() {
        Bundle data = new Bundle();
        data.putString(KEY_DATE, mFilterDate);
        IntentUtils.startActivity(getActivity(), EventDetail.class, data);
    }
}
