package framgia.vn.framgiacrb.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import framgia.vn.framgiacrb.R;
import framgia.vn.framgiacrb.activity.CreateEventActvity;
import framgia.vn.framgiacrb.activity.DetailActivity;
import framgia.vn.framgiacrb.activity.MainActivity;
import framgia.vn.framgiacrb.adapter.ListEventAdapter;
import framgia.vn.framgiacrb.constant.Constant;
import framgia.vn.framgiacrb.data.OnLoadEventListener;
import framgia.vn.framgiacrb.data.local.EventRepositoriesLocal;
import framgia.vn.framgiacrb.data.model.Event;
import framgia.vn.framgiacrb.data.remote.EventRepositories;
import framgia.vn.framgiacrb.fragment.item.ItemMonth;
import framgia.vn.framgiacrb.object.EventParcelabler;
import framgia.vn.framgiacrb.utils.Connectivity;
import framgia.vn.framgiacrb.utils.SimpleItemTouchHelperCallback;
import framgia.vn.framgiacrb.utils.TimeUtils;
import io.realm.Realm;

/**
 * Created by nghicv on 04/07/2016.
 */
public class EventsFragment extends Fragment {

    private View mViewEvents;
    private RecyclerView mRecyclerViewEvents;
    private FloatingActionButton mFloatingActionButton;
    private ListEventAdapter mAdapter;
    private List<Object> mDatas = new ArrayList<>();
    private int mFirstMonth;
    private int mPositionToday;
    private int mFirstYear;
    private int mLastMonth;
    private int mLastYear;
    private boolean isLoading;
    private int mOldDataSize;
    private EventRepositories mEventRepositories;
    private EventRepositoriesLocal mEventRepositoriesLocal;
    private Realm mRealm;
    private BroadcastReceiver mBroadcastReceiverToday;
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mViewEvents = inflater.inflate(R.layout.fragment_events, container, false);
        initViews();
        framgia.vn.framgiacrb.data.model.Calendar calendar = new framgia.vn.framgiacrb.data.model.Calendar();
        calendar.setId(6);
        mRealm = Realm.getDefaultInstance();
        mEventRepositoriesLocal = new EventRepositoriesLocal(mRealm);
        mEventRepositories = new EventRepositories();
        mEventRepositories.setOnLoadEventListener(new OnLoadEventListener() {
            @Override
            public void onSuccess() {
                try {
                    initDatas();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError() {

            }
        });

        if (Connectivity.isConnected(getActivity())) {
            mEventRepositories.getEventsByCalendar(MainActivity.sAuthToken, calendar, getActivity());
        } else {
            try {
                initDatas();
            } catch (ParseException e) {
                e.printStackTrace();
            }
            Toast.makeText(getActivity(), getActivity().getString(R.string.message_not_connect), Toast.LENGTH_SHORT).show();
        }

        mBroadcastReceiverToday = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(MainActivity.ACTION_TODAY)) {
                    LinearLayoutManager linearLayoutManager = (LinearLayoutManager) mRecyclerViewEvents.getLayoutManager();
                    int firstVisibleItem = linearLayoutManager.findFirstVisibleItemPosition();
                    if (firstVisibleItem > mPositionToday) {
                        mRecyclerViewEvents.scrollToPosition(mPositionToday - 2);
                    } else {
                        mRecyclerViewEvents.scrollToPosition(mPositionToday + 5);
                    }
                }
            }
        };
        getActivity().registerReceiver(mBroadcastReceiverToday, new IntentFilter(MainActivity.ACTION_TODAY));
        return mViewEvents;
    }

    private void initViews() {
        mRecyclerViewEvents = (RecyclerView) mViewEvents.findViewById(R.id.rv_events);
        mRecyclerViewEvents.setLayoutManager(new LinearLayoutManager(getActivity()));
        mFloatingActionButton = (FloatingActionButton) mViewEvents.findViewById(R.id.fab);
        mAdapter = new ListEventAdapter(getActivity(), mDatas);
        mRecyclerViewEvents.setAdapter(mAdapter);
        ItemTouchHelper.Callback callback =
                new SimpleItemTouchHelperCallback(mAdapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(mRecyclerViewEvents);
        mAdapter.setOnEventSelectedListener(new ListEventAdapter.OnEventSelectedListener() {
            @Override
            public void onSelected(int position) {
                startDetailActivity(position);
            }
        });
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getActivity(), CreateEventActvity.class));
            }
        });

        mRecyclerViewEvents.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int totalItemCount = recyclerView.getLayoutManager().getItemCount();
                int lastVisibleItem = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
                int firstVisibleItem = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                if (dy > 0) {
                    if (totalItemCount - 10 < lastVisibleItem + 1) {
                        if (!isLoading) {
                            isLoading = true;
                            try {
                                loadDatasForNextMonth();
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            mAdapter.notifyDataSetChanged();
                            isLoading = false;
                        }
                    }
                } else {
                    if (10 > firstVisibleItem) {

                        if (!isLoading) {
                            isLoading = true;
                            try {
                                loadDatasForPrevMonth();
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            isLoading = false;
                        }

                    }
                }
            }
        });
    }

    private void startDetailActivity(int position) {
        Event event = (Event)mDatas.get(position);
        EventParcelabler eventParcelabler = new EventParcelabler(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getStartTime(),
            event.getFinishTime(),
            event.getStatus(),
            event.getRepeatType(),
            event.getRepeatEvery(),
            event.getEndDate(),
            event.getExceptionDate(),
            event.getType(),
            event.getEventId()
        );
        Intent intent = new Intent(getActivity(), DetailActivity.class);
        intent.putExtra(Constant.INTENT_DATA, eventParcelabler);
        startActivity(intent);
    }

    private void initDatas() throws ParseException {
        mDatas.clear();
        Calendar calendar = Calendar.getInstance();
        Date today = calendar.getTime();
        int month = Integer.parseInt(android.text.format.DateFormat.format("MM", today).toString());
        mLastMonth = month;
        mFirstMonth = month;
        String stringMonth = android.text.format.DateFormat.format("MMM", today).toString();
        mLastYear = Integer.parseInt(android.text.format.DateFormat.format("yyyy", today).toString());
        mFirstYear = mLastYear;
        calendar.set(mLastYear, month - 1, 1);
        Date date = calendar.getTime();
        mDatas.add(new ItemMonth(month, stringMonth, mLastYear));
        boolean isTimelineAdded = false;
        while (month < mLastMonth + 1) {

            mDatas.add(date);
            if (date.equals(today)) {
                mPositionToday = mDatas.size();
                List<Event> events = mEventRepositoriesLocal.getEventByDate(TimeUtils.formatDate(date));
                for (int i = 0; i < events.size(); i++) {
                    Event event = events.get(i);
                    if (event.getStartTime().getTime() > today.getTime() && !isTimelineAdded) {
                        mDatas.add(null);
                        isTimelineAdded = true;
                    }
                    mDatas.add(event);
                }
                if (!isTimelineAdded) {
                    mDatas.add(null);
                }
            } else {
                mDatas.addAll(mEventRepositoriesLocal.getEventByDate(TimeUtils.formatDate(date)));
            }

            calendar.add(Calendar.DATE, 1);
            date = calendar.getTime();
            month = Integer.parseInt(android.text.format.DateFormat.format("MM", date).toString());
        }
        mAdapter.notifyDataSetChanged();
        if (mPositionToday > 20) {
            loadDatasForNextMonth();
        }
        mRecyclerViewEvents.scrollToPosition(mPositionToday - 2);
    }

    private void loadDatasForNextMonth() throws ParseException {
        Calendar calendar = Calendar.getInstance();
        if (mLastMonth < 12) {
            mLastMonth += 1;
        } else {
            mLastMonth = 1;
            mLastYear += 1;
        }
        int month = mLastMonth;
        calendar.set(mLastYear, month - 1, 1);
        Date date = calendar.getTime();
        String stringMonth = android.text.format.DateFormat.format("MMM", date).toString();
        mDatas.add(new ItemMonth(month, stringMonth, mLastYear));
        while ((month < mLastMonth + 1) && !(mLastMonth == 12 && month == 1)) {
            mDatas.add(date);
            mDatas.addAll(mEventRepositoriesLocal.getEventByDate(TimeUtils.formatDate(date)));
            calendar.add(Calendar.DATE, 1);
            date = calendar.getTime();
            month = Integer.parseInt(android.text.format.DateFormat.format("MM", date).toString());
        }
    }

    private void loadDatasForPrevMonth() throws ParseException {
        mOldDataSize = mDatas.size();
        Calendar calendar = Calendar.getInstance();
        if (mFirstMonth > 1) {
            mFirstMonth -= 1;
        } else {
            mFirstMonth = 12;
            mFirstYear -= 1;
        }
        int month = mFirstMonth;
        calendar.set(mFirstYear, month - 1, 1);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        Date date = calendar.getTime();
        String stringMonth = android.text.format.DateFormat.format("MMM", date).toString();
        while ((month >= mFirstMonth) && !(mFirstMonth == 1 && month == 12)) {
            mDatas.add(0, date);
            mDatas.addAll(1, mEventRepositoriesLocal.getEventByDate(TimeUtils.formatDate(date)));
            calendar.add(Calendar.DATE, -1);
            date = calendar.getTime();
            month = Integer.parseInt(android.text.format.DateFormat.format("MM", date).toString());
        }
        mDatas.add(0, new ItemMonth(mFirstMonth, stringMonth, mFirstYear));
        mAdapter.notifyItemRangeInserted(0, mDatas.size() - mOldDataSize);
        mPositionToday = mPositionToday + mDatas.size() - mOldDataSize;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
        getActivity().unregisterReceiver(mBroadcastReceiverToday);
    }
}
