package com.sadwave.events.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ShareCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.arellomobile.mvp.MvpAppCompatActivity
import com.arellomobile.mvp.presenter.InjectPresenter
import com.arellomobile.mvp.presenter.ProvidePresenter
import com.sadwave.events.mvp.MainPresenter
import com.sadwave.events.mvp.MainView
import com.sadwave.events.mvp.State
import com.sadwave.events.net.CityEntity
import com.sadwave.events.net.EventEntity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_content.*
import org.koin.android.ext.android.get
import android.provider.CalendarContract.Events
import android.provider.CalendarContract
import android.widget.Toast
import com.sadwave.events.util.SadDateFormatter
import java.util.*
import android.net.Uri
import android.view.View
import com.google.android.material.navigation.NavigationView
import com.sadwave.events.R


class MainActivity : MvpAppCompatActivity(), MainView, CitiesAdapter.Listener,
    EventsAdapter.Listener {
    private val sadDateFormatter: SadDateFormatter = get()
    private val citiesAdapter: CitiesAdapter = CitiesAdapter(this)
    private val eventsAdapter: EventsAdapter = EventsAdapter(this, sadDateFormatter)

    @InjectPresenter
    lateinit var presenter: MainPresenter

    @ProvidePresenter
    fun provide(): MainPresenter = get()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_NoActionBar)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        cities.adapter = citiesAdapter
        events.adapter = eventsAdapter

        presenter.refresh(loadLastCityName())
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onState(state: State) {
        when (state) {
            State.Loading -> {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                progressLayout.showLoading()
            }
            is State.Error -> {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                progressLayout.showError(
                    R.drawable.ic_error_black_24dp,
                    getString(R.string.error_title),
                    getString(R.string.error_description),
                    getString(R.string.error_btn_text)
                ) {
                    presenter.refresh(loadLastCityName())
                }
            }
            is State.OnData -> {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                progressLayout.showContent()
                toolbar.title = state.currentCity.name
                citiesAdapter.setData(state.cities, state.currentCity)
                eventsAdapter.events = state.events
            }
        }
    }

    override fun onCityClick(city: CityEntity) {
        if (city.name == loadLastCityName()) {
            drawerLayout.closeDrawers()
            return
        }
        presenter.selectCity(city)
        saveLastCityName(city.name)
    }

    override fun onEventClick(event: EventEntity) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(event.url)
        startActivity(intent)
    }

    override fun onEventShare(event: EventEntity) {
        ShareCompat.IntentBuilder.from(this)
            .setType("text/plain")
            .setChooserTitle(getString(R.string.share, event.name))
            .setText(event.url)
            .startChooser()
    }

    override fun onEventAddToCalendar(event: EventEntity) {
        val date = sadDateFormatter.parseDate(event.date?.date)
        if (date == null) {
            showWrongDateMessage()
            return
        }

        val calendar = Calendar.getInstance()
        val time = sadDateFormatter.parseTime(event.date?.time)
        var hasTime = false
        if (time != null) {
            hasTime = true
            date.time += time.time
        }
        calendar.time = date

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(Events.CONTENT_URI)
            .putExtra(Events.TITLE, event.name ?: getString(R.string.gig_default))
            .putExtra(Events.DESCRIPTION, event.overview ?: "")
            .putExtra(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
        if (hasTime) {
            intent.putExtra(
                CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                calendar.timeInMillis + TimeZone.getDefault().rawOffset
            )
        }
        startActivity(intent)
    }

    private fun showWrongDateMessage() {
        Toast.makeText(this, "Не удалось сохранить мероприятие", Toast.LENGTH_SHORT).show()
    }

    private fun saveLastCityName(name: String?) {
        val sharedPref = getSharedPreferences(getString(R.string.preferences_file_name), Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString(getString(R.string.preferences_last_city), name)
            apply()
        }
    }

    private fun loadLastCityName(): String? {
        val sharedPref = getSharedPreferences(getString(R.string.preferences_file_name), Context.MODE_PRIVATE) ?: return null
        return sharedPref.getString(getString(R.string.preferences_last_city), null)
    }
}
