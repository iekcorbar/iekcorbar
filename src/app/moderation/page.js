'use client';

import React, { useState, useEffect, Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useUser, useSiteContent } from '../providers';
import RichTextEditor from '@/components/RichTextEditor';
import {
  ShieldAlert,
  Users,
  Lightbulb,
  Calendar,
  Terminal, 
  AlertTriangle,
  UserX,
  VolumeX,
  FileSpreadsheet,
  Clock,
  ShieldCheck,
  Send,
  HelpCircle,
  TrendingDown,
  Trash2,
  Edit,
  Plus,
  Image as ImageIcon,
  X,
  Sparkles,
  Tag,
  Compass,
  Shield,
  ThumbsUp,
  Flag,
  Bug,
  Settings,
  Map,
  BookOpen,
  Folder,
  FolderOpen,
  FileText,
  ChevronDown,
  ChevronRight,
  Eye,
  History,
  Undo2,
  MessageSquare,
  RefreshCw,
  Hammer,
  Paperclip,
  Ban,
  UserCheck,
  Check,
  Search,
  Bell,
  Mail,
  Archive,
  StickyNote,
  ChevronUp,
  GripVertical,
  Newspaper,
  Rocket,
  ExternalLink,
  FolderLock,
  Upload,
  Download,
  BarChart3,
  ScrollText,
} from 'lucide-react';
import { renderMarkdown } from '@/lib/markdown';
import AnalyticsTab from '@/components/AnalyticsTab';
import StoryRoomTab from '@/components/StoryRoomTab';
import { StaffSidebar, StaffOverview } from './StaffShell';
import AuditLogView from './AuditLogView';
import ProfileBiosPanel from './ProfileBiosPanel';
import { SITE_CONTENT_DEFAULTS, SITE_CONTENT_CATEGORY_LABELS } from '@/lib/site-content';
import { STAFF_TABS, SETTINGS_SUBTAB_KEYS, DEFAULT_MODERATOR_TABS, roleRank, compareRoles, ROLE_COLORS, roleBadgeClass, DEFAULT_CUSTOM_ROLE_COLOR, DEFAULT_MODERATOR_COLOR } from '@/lib/staff-tabs';
import PlayerAvatar from '@/components/PlayerAvatar';
import { bugCategoryLabel, BUG_STATUS_LABELS, BUG_CATEGORIES, isBugReportArchived } from '@/lib/bug-categories';

// Icon + accent-color choices for the top-level tab bar and the Settings
// sub-tab bar — page-display concerns, deliberately kept out of
// src/lib/staff-tabs.js (the shared manifest Role Manager also reads, where
// neither an icon nor a color means anything).
const TAB_ICONS = {
  moderation: UserX, reports: Flag, bugs: Bug, events: Calendar, wiki: BookOpen,
  updates: RefreshCw, news: Newspaper, polls: ThumbsUp, story: ScrollText, applications: ShieldCheck, access: Shield, analytics: BarChart3, console: Terminal, settings: Settings
};
const CRIMSON_TABS = new Set(['reports', 'bugs', 'access', 'console']);

// Sidebar grouping only: each hub just lists existing tabs, so every tab keeps
// its own permission (STAFF_TABS), ?tab= deep link and API routes. A tab that
// is in no hub (e.g. one added to STAFF_TABS later) still gets its own sidebar
// item, so nothing can silently disappear from the panel.
const NAV_HUBS = [
  { key: 'hub-moderation', label: 'Moderation', Icon: Shield, description: 'Sanctions and staff notes, player reports, and the server ban list / whitelist.', tabs: ['moderation', 'reports', 'access'] },
  { key: 'hub-community', label: 'Community', Icon: Users, description: 'Bug reports, staff applications, events and polls.', tabs: ['bugs', 'applications', 'events', 'polls'] },
  { key: 'hub-content', label: 'Content', Icon: BookOpen, description: 'Wiki, updates, news and the Story Room.', tabs: ['wiki', 'updates', 'news', 'story'] },
  { key: 'hub-server', label: 'Server', Icon: Terminal, description: 'RCON console and website/app analytics.', tabs: ['console', 'analytics'] }
];

const SETTINGS_TAB_ICONS = {
  settings_general: Settings, settings_carousel: ImageIcon, settings_guide: Compass,
  settings_rules: FileSpreadsheet, settings_tips: Lightbulb, settings_vote: ThumbsUp,
  settings_projects: Hammer, settings_content: Tag, settings_cleanup: UserCheck
};

function ModerationContent() {
  const { user, setIsLoginOpen } = useUser();
  const siteContent = useSiteContent();
  const searchParams = useSearchParams();
  const [users, setUsers] = useState([]);
  const [pendingRegistrations, setPendingRegistrations] = useState([]);
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);

  // Per-role Staff Panel permissions — myPermissions is what the *current*
  // user's own role can access (drives the Auth Guard + which tabs render,
  // fetched for every staff member), roles/loadingRoles is the full editable
  // list (admin-only, powers the Roles & Permissions screen in Settings).
  // null (not []) means "still loading" so the Auth Guard below doesn't
  // flash Access Denied at a legitimate custom-role staffer before their
  // permissions have arrived.
  const [myPermissions, setMyPermissions] = useState(null); // { tabs, isStaff, isAdmin }
  const [roles, setRoles] = useState([]);
  const [loadingRoles, setLoadingRoles] = useState(false);
  const [roleFormName, setRoleFormName] = useState('');
  const [roleFormLabel, setRoleFormLabel] = useState('');
  const [roleFormTabs, setRoleFormTabs] = useState([]);
  const [roleFormColor, setRoleFormColor] = useState(DEFAULT_CUSTOM_ROLE_COLOR);
  const [roleFormEditing, setRoleFormEditing] = useState(null); // name of the role being edited, or null for "new"
  const [roleSaving, setRoleSaving] = useState(false);
  const [roleError, setRoleError] = useState('');
  const [roleDeleteConfirm, setRoleDeleteConfirm] = useState(null); // role name pending deletion
  // Filters the checkbox list in the role create/edit form by label/
  // description — mainly useful for the 9-item "Settings Tools" group,
  // same idea as the Wiki editor's own directory search.
  const [roleTabSearch, setRoleTabSearch] = useState('');

  // Newsletter (Settings > Newsletter, admin-only)
  const [newsletterHistory, setNewsletterHistory] = useState([]);
  const [loadingNewsletterHistory, setLoadingNewsletterHistory] = useState(false);
  const [newsletterSubject, setNewsletterSubject] = useState('');
  const [newsletterBody, setNewsletterBody] = useState('');
  const [sendingNewsletter, setSendingNewsletter] = useState(false);
  const [newsletterResult, setNewsletterResult] = useState(null);
  const [newsletterError, setNewsletterError] = useState('');
  const [newsletterConfirming, setNewsletterConfirming] = useState(false);

  // Admin Shared Files (Settings > Shared Files, admin-only — see
  // api/admin/files) — a place for admins to pass plugins/configs/misc
  // files to each other.
  const [adminFiles, setAdminFiles] = useState([]);
  const [loadingAdminFiles, setLoadingAdminFiles] = useState(false);
  const [adminFileDescription, setAdminFileDescription] = useState('');
  const [adminFileToUpload, setAdminFileToUpload] = useState(null);
  const [uploadingAdminFile, setUploadingAdminFile] = useState(false);
  const [adminFileError, setAdminFileError] = useState('');

  // Tabs state — initialize from URL query param ?tab= for notification deep-linking
  const [activeTab, setActiveTab] = useState(() => {
    const tab = searchParams.get('tab');
    return tab || 'overview';
  });

  // Overview tab data (counters + recent activity) from /api/admin/dashboard,
  // already filtered server-side to what this role may see. Also feeds the
  // sidebar's attention badges, so it loads once up front and refreshes
  // every minute instead of only while the Overview tab is open.
  const [dashboard, setDashboard] = useState(null);
  const [dashboardLoading, setDashboardLoading] = useState(true);
  const [dashboardError, setDashboardError] = useState('');
  const loadDashboard = React.useCallback(async () => {
    try {
      const res = await fetch('/api/admin/dashboard', { cache: 'no-store' });
      if (!res.ok) throw new Error(res.status === 403 ? 'No access' : 'Could not load the overview');
      setDashboard(await res.json());
      setDashboardError('');
    } catch (err) {
      setDashboardError(err.message || 'Could not load the overview');
    } finally {
      setDashboardLoading(false);
    }
  }, []);
  const dashboardEnabled = !!myPermissions?.isStaff;
  useEffect(() => {
    if (!dashboardEnabled) return;
    loadDashboard();
    const timer = setInterval(loadDashboard, 60000);
    return () => clearInterval(timer);
  }, [dashboardEnabled, loadDashboard]);
  const [settingsSubTab, setSettingsSubTab] = useState('general'); // 'general', 'carousel', 'guide', 'rules', 'vote', 'content'
  // Filter boxes for the main tab bar and the Settings sub-tab bar — pure
  // display filters (narrow which buttons show), not additional access
  // control, so they're plain local state rather than anything server-side.
  const [mainTabSearch, setMainTabSearch] = useState('');
  const [settingsTabSearch, setSettingsTabSearch] = useState('');
  const [siteContentDraft, setSiteContentDraft] = useState(null); // null until opened, then a local copy to edit
  const [siteContentSaving, setSiteContentSaving] = useState(false);
  const [carouselItems, setCarouselItems] = useState([]);
  const [draggedSlideId, setDraggedSlideId] = useState(null);
  const [carouselSets, setCarouselSets] = useState([]);
  const [selectedCarouselSetId, setSelectedCarouselSetId] = useState(null);
  const [loadingCarouselItems, setLoadingCarouselItems] = useState(false);
  const [newSetName, setNewSetName] = useState('');
  const [creatingSet, setCreatingSet] = useState(false);
  const [renamingSetId, setRenamingSetId] = useState(null);
  const [renameSetName, setRenameSetName] = useState('');
  const [carouselSetError, setCarouselSetError] = useState('');
  const [polls, setPolls] = useState([]);
  const [events, setEvents] = useState([]);
  const [registrations, setRegistrations] = useState([]);
  const [loadingEvents, setLoadingEvents] = useState(true);

  // Polls Form state
  const [pollEditId, setPollEditId] = useState(null);
  const [pollQuestion, setPollQuestion] = useState('');
  const [pollOptions, setPollOptions] = useState(['', '']);

  // Carousel Form state
  const [editingSlideId, setEditingSlideId] = useState(null);
  const [slideTitle, setSlideTitle] = useState('');
  // Which sets this slide belongs to — a slide can be a member of several
  // at once (see db.saveCarouselItem's setIds), so re-uploading the same
  // image into every set that wants it is no longer necessary.
  const [slideSetIds, setSlideSetIds] = useState([]);
  const [slideDesc, setSlideDesc] = useState('');
  const [slideImageSrc, setSlideImageSrc] = useState('');
  const [imageError, setImageError] = useState('');
  const [isProcessingImage, setIsProcessingImage] = useState(false);

  // Forms state (moderation)
  const [targetUsername, setTargetUsername] = useState('');
  const [type, setType] = useState('kick');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  // Quick Action Modal States
  const [actionModalOpen, setActionModalOpen] = useState(false);
  const [actionTargetUsername, setActionTargetUsername] = useState('');
  const [actionType, setActionType] = useState('kick'); // 'kick' or 'ban'
  const [actionReason, setActionReason] = useState('');
  const [actionSubmitting, setActionSubmitting] = useState(false);
  const [actionError, setActionError] = useState('');
  const [actionSuccess, setActionSuccess] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // Search and Role filters
  const [searchMember, setSearchMember] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');
  const [quickSanctionTarget, setQuickSanctionTarget] = useState(null);
  const [quickReason, setQuickReason] = useState('');
  // GDPR erasure ("Right to be Forgotten") — the actual staff action behind
  // the Privacy Policy's promise, see api/admin/gdpr-delete. Admin-only,
  // irreversible, kept separate from the kick/ban/note quick actions above.
  const [gdprDeleteConfirm, setGdprDeleteConfirm] = useState(null); // the member object pending deletion
  const [gdprDeleting, setGdprDeleting] = useState(false);
  const [gdprError, setGdprError] = useState('');

  // Updates Manager states
  const [updates, setUpdates] = useState([]);
  const [updateCategories, setUpdateCategories] = useState([]);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [updateId, setUpdateId] = useState('');
  const [updateTitle, setUpdateTitle] = useState('');
  const [updateCategory, setUpdateCategory] = useState('General');
  const [updateContent, setUpdateContent] = useState('');
  const [loadingUpdates, setLoadingUpdates] = useState(true);

  // News & Upcoming Manager states
  const [newsPosts, setNewsPosts] = useState([]);
  const [newsTopics, setNewsTopics] = useState([]);
  const [newNewsTopicName, setNewNewsTopicName] = useState('');
  const [newsPostId, setNewsPostId] = useState('');
  const [newsTitle, setNewsTitle] = useState('');
  const [newsType, setNewsType] = useState('news');
  const [newsTopic, setNewsTopic] = useState('General');
  const [newsContent, setNewsContent] = useState('');
  const [newsLink, setNewsLink] = useState('');
  const [loadingNews, setLoadingNews] = useState(true);

  // Guide, Rules, Vote, Reports, Settings states
  const [guideSteps, setGuideSteps] = useState([]);
  const [guideEditId, setGuideEditId] = useState('');
  const [guideTitle, setGuideTitle] = useState('');
  const [guideContent, setGuideContent] = useState('');
  const [guideAttachmentFile, setGuideAttachmentFile] = useState(null);
  const [guideExistingAttachment, setGuideExistingAttachment] = useState(null); // { attachmentUrl, attachmentName } when editing a step that has one
  const [guideRemoveAttachment, setGuideRemoveAttachment] = useState(false);
  const [rules, setRules] = useState([]);
  const [ruleEditId, setRuleEditId] = useState('');
  const [ruleTitle, setRuleTitle] = useState('');
  const [ruleContent, setRuleContent] = useState('');
  const [tips, setTips] = useState([]);
  const [tipEditId, setTipEditId] = useState('');
  const [tipContent, setTipContent] = useState('');
  const [tipActive, setTipActive] = useState(true);
  const [voteSites, setVoteSites] = useState([]);
  const [voteEditId, setVoteEditId] = useState('');
  const [voteName, setVoteName] = useState('');
  const [voteUrl, setVoteUrl] = useState('');
  const [voteIcon, setVoteIcon] = useState('🗳️');
  const [reports, setReports] = useState([]);
  const [staffApplications, setStaffApplications] = useState([]);
  const [saNotesDrafts, setSaNotesDrafts] = useState({}); // applicationId -> string
  const [saDecidingId, setSaDecidingId] = useState(null);
  const [saContactTarget, setSaContactTarget] = useState(null); // { id, applicantUsername } or null
  const [saContactChannel, setSaContactChannel] = useState('bell');
  const [saContactMessage, setSaContactMessage] = useState('');
  const [saContactSending, setSaContactSending] = useState(false);
  const [saContactError, setSaContactError] = useState('');
  const [bugReports, setBugReports] = useState([]);
  const [bugFilter, setBugFilter] = useState('active'); // 'active' | 'archived'
  const [bugThreads, setBugThreads] = useState({}); // reportId -> messages[]
  const [expandedBugId, setExpandedBugId] = useState(null);
  const [bugNotesDrafts, setBugNotesDrafts] = useState({}); // reportId -> string
  const [bugNotesSavingId, setBugNotesSavingId] = useState(null);
  const [bugCategoryDrafts, setBugCategoryDrafts] = useState({}); // reportId -> { category, customCategory } while editing
  const [bugCategorySavingId, setBugCategorySavingId] = useState(null);
  const [contactTarget, setContactTarget] = useState(null); // { id, subject } or null
  const [contactChannel, setContactChannel] = useState('bell');
  const [contactMessage, setContactMessage] = useState('');
  const [contactSending, setContactSending] = useState(false);
  const [contactError, setContactError] = useState('');
  const [duplicateTarget, setDuplicateTarget] = useState(null); // { id, subject } or null
  const [duplicateOfId, setDuplicateOfId] = useState('');
  const [accessList, setAccessList] = useState({ banned: [], whitelistedUnregistered: [], whitelistedTotal: 0 });
  const [settingsJavaVersion, setSettingsJavaVersion] = useState('');
  const [settingsMapUrl, setSettingsMapUrl] = useState('');
  const [settingsServerAddress, setSettingsServerAddress] = useState('');
  const [settingsSaved, setSettingsSaved] = useState(false);

  // Admin-only raw RCON terminal (Console tab)
  const [consoleLog, setConsoleLog] = useState([]);
  const [consoleInput, setConsoleInput] = useState('');
  const [consoleSending, setConsoleSending] = useState(false);
  const [consoleError, setConsoleError] = useState('');

  // Community Projects state
  const [projects, setProjects] = useState([]);
  const [projectEditId, setProjectEditId] = useState('');
  const [projectTitle, setProjectTitle] = useState('');
  const [projectCategory, setProjectCategory] = useState('Building');
  const [projectDescription, setProjectDescription] = useState('');
  const [projectStatus, setProjectStatus] = useState('open');
  const [projectMaxParticipants, setProjectMaxParticipants] = useState('');

  // Account Cleanup (inactivity purge) state
  const [inactiveCandidates, setInactiveCandidates] = useState([]);
  const [inactivityDays, setInactivityDays] = useState(90);
  const [loadingInactive, setLoadingInactive] = useState(false);
  const [inactiveLoadError, setInactiveLoadError] = useState('');
  const [selectedInactiveEmails, setSelectedInactiveEmails] = useState([]);
  const [purgingInactive, setPurgingInactive] = useState(false);
  const [purgeResults, setPurgeResults] = useState(null);

  // Wiki Lore States
  const [wikiPages, setWikiPages] = useState([]);
  const [loadingWiki, setLoadingWiki] = useState(true);
  const [wikiEditId, setWikiEditId] = useState(null);
  const [wikiTitle, setWikiTitle] = useState('');
  // No hardcoded starting category — see wikiCategories below for why.
  const [wikiCategory, setWikiCategory] = useState('');
  const [wikiContent, setWikiContent] = useState('');
  const [wikiParentId, setWikiParentId] = useState('');
  const [isWikiCustomCategory, setIsWikiCustomCategory] = useState(false);
  const [wikiCustomCategory, setWikiCustomCategory] = useState('');
  const [wikiUploadedImages, setWikiUploadedImages] = useState([]);
  const [wikiImageError, setWikiImageError] = useState('');
  // Real file attachments (PDFs/screenshots) — separate from the inline-
  // base64-image feature above, which can't handle PDFs at all.
  const [wikiAttachmentUploading, setWikiAttachmentUploading] = useState(false);
  const [wikiAttachmentError, setWikiAttachmentError] = useState('');
  // Markup cheat-sheet toggle — staff kept asking "how do I make this bold /
  // orange?" with no answer anywhere in the editor itself. Collapsed by
  // default so it doesn't push the actual form down for people who already
  // know the syntax.
  const [wikiLegendOpen, setWikiLegendOpen] = useState(false);
  // Directory-finding controls — the tree used to be one long undifferentiated
  // scrolling list of every top-level page with no search and no grouping,
  // which is exactly what made "find the right article to edit" a chore as
  // the wiki grew. Search matches sitewide (title/category/content); the
  // category filter chips narrow the tree to one chapter at a time; and
  // collapsed groups let staff fold away chapters they aren't touching.
  const [wikiTreeSearch, setWikiTreeSearch] = useState('');
  const [wikiTreeCategoryFilter, setWikiTreeCategoryFilter] = useState('');
  const [wikiCollapsedCategories, setWikiCollapsedCategories] = useState({});
  const [selectedWikiPage, setSelectedWikiPage] = useState(null);
  const [wikiActiveViewTab, setWikiActiveViewTab] = useState('article');
  const [wikiPreviewRevisionContent, setWikiPreviewRevisionContent] = useState(null);
  // Explicit confirm modals for delete actions — replaces the old bare
  // browser confirm() so staff actually see what's about to be removed
  // (a page's subtopic count, or every page inside a category) before
  // committing to it.
  const [wikiDeleteConfirm, setWikiDeleteConfirm] = useState(null); // the page object pending deletion
  const [wikiCategoryDeleteConfirm, setWikiCategoryDeleteConfirm] = useState(null); // category name pending deletion
  const [wikiCategoryDeleting, setWikiCategoryDeleting] = useState(false);
  // Inline rename of a category (pencil icon in Manage Categories) + the
  // one-click bulk "strip numbering" cleanup for legacy "00: Foo" style
  // category names left over from before drag-reorder existed.
  const [wikiCategoryRenameEdit, setWikiCategoryRenameEdit] = useState(null); // category name currently being edited
  const [wikiCategoryRenameValue, setWikiCategoryRenameValue] = useState('');
  const [wikiCategoryRenaming, setWikiCategoryRenaming] = useState(false);
  const [wikiStripNumberingRunning, setWikiStripNumberingRunning] = useState(false);

  // Purely data-driven — this used to always union in ['Races', 'Jobs',
  // 'Brewery', 'Lands'] (the site's original seed categories) regardless of
  // whether any page actually used them any more. Once staff had renamed/
  // reorganized everything into real categories, those four became
  // permanent ghosts: always listed, always 0 pages, and (since "Manage
  // Categories" disables deleting an empty category — nothing to delete)
  // impossible to ever get rid of. Real categories only, now.
  const wikiPagesList = Array.isArray(wikiPages) ? wikiPages : [];
  const wikiCategories = Array.from(new Set(wikiPagesList.map(p => p.category)));

  // Directory tree: search always matches sitewide (title/category/content);
  // the category chip further narrows it. Either one flattens the tree into
  // a flat result list (nesting stops being meaningful once you're hunting
  // for a specific match rather than browsing a chapter).
  const wikiTreeQuery = wikiTreeSearch.trim().toLowerCase();
  const wikiTreeIsSearching = wikiTreeQuery.length > 0;
  const wikiTreeFiltered = wikiPagesList.filter(p => {
    const matchesCategory = !wikiTreeCategoryFilter || p.category === wikiTreeCategoryFilter;
    const matchesSearch = !wikiTreeIsSearching ||
      p.title.toLowerCase().includes(wikiTreeQuery) ||
      p.category.toLowerCase().includes(wikiTreeQuery) ||
      (p.content || '').toLowerCase().includes(wikiTreeQuery);
    return matchesCategory && matchesSearch;
  });
  const wikiTreeIsFlat = wikiTreeIsSearching || !!wikiTreeCategoryFilter;
  // Grouped (browsing) view — only built/used when not flattened.
  const wikiTreeGroups = wikiCategories
    .map(cat => ({ category: cat, topPages: wikiPagesList.filter(p => !p.parentId && p.category === cat) }))
    .filter(g => g.topPages.length > 0);

  // Event Form CRUD States
  const [eventEditId, setEventEditId] = useState(null);
  const [eventTitle, setEventTitle] = useState('');
  const [eventDesc, setEventDesc] = useState('');
  const [eventDate, setEventDate] = useState('');
  const [eventTime, setEventTime] = useState('');
  const [eventTimezone, setEventTimezone] = useState('UTC+2');
  // Replaces the old "Poster Theme" dropdown (2 hardcoded options: derby/boss)
  // with a real image upload, same runtime-upload pattern as guide attachments.
  const [eventImageFile, setEventImageFile] = useState(null);
  const [eventImagePreview, setEventImagePreview] = useState('');
  const [eventExistingImage, setEventExistingImage] = useState(''); // current uploaded poster URL when editing
  const [eventRemoveImage, setEventRemoveImage] = useState(false);

  const fetchProjects = async () => {
    try {
      const res = await fetch('/api/projects');
      const data = await res.json();
      if (res.ok && data.projects) setProjects(data.projects);
    } catch (err) {
      console.error("Failed to load projects", err);
    }
  };

  // What the logged-in user's own role can access — fetched for anyone
  // logged in (not just the hardcoded admin/moderator) so a custom staff
  // role created via Role Manager gets into the Staff Panel at all. See
  // src/lib/permissions.js for the real server-side enforcement this
  // mirrors; this call itself is what the Auth Guard and tab nav below key
  // off of.
  const fetchMyPermissions = async () => {
    try {
      const res = await fetch('/api/permissions/me');
      const data = await res.json();
      setMyPermissions({ tabs: data.tabs || [], isStaff: !!data.isStaff, isAdmin: !!data.isAdmin });
    } catch (err) {
      console.error("Failed to load own permissions", err);
      setMyPermissions({ tabs: [], isStaff: false, isAdmin: false });
    }
  };

  // Full editable role list — admin-only (the API rejects anyone else), so
  // only fetched once we know the user is actually admin.
  const fetchRoles = async () => {
    setLoadingRoles(true);
    try {
      const res = await fetch('/api/admin/roles');
      const data = await res.json();
      if (res.ok && data.roles) setRoles(data.roles);
    } catch (err) {
      console.error("Failed to load roles", err);
    } finally {
      setLoadingRoles(false);
    }
  };

  function openNewRoleForm() {
    setRoleFormEditing(null);
    setRoleFormName('');
    setRoleFormLabel('');
    setRoleFormTabs([]);
    setRoleFormColor(DEFAULT_CUSTOM_ROLE_COLOR);
    setRoleError('');
    setRoleTabSearch('');
  }

  function openEditRoleForm(role) {
    setRoleFormEditing(role.name);
    setRoleFormName(role.name);
    setRoleFormLabel(role.label);
    setRoleFormColor(role.color || (role.name === 'moderator' ? DEFAULT_MODERATOR_COLOR : DEFAULT_CUSTOM_ROLE_COLOR));
    // A role saved before Settings was split into per-tool permissions
    // (2026-09-13) may still only carry the old bare 'settings' key — expand
    // it here too, so the checkbox form shows what this role can ACTUALLY
    // do right now (see staff-tabs.js/permissions.js's getRoleTabs, which
    // does the same expansion server-side) instead of showing every
    // Settings Tools checkbox unchecked.
    let tabs = Array.isArray(role.tabs) ? role.tabs : [];
    if (tabs.includes('settings')) {
      tabs = [...new Set([...tabs, ...SETTINGS_SUBTAB_KEYS])];
    }
    setRoleFormTabs(tabs);
    setRoleError('');
  }

  function toggleRoleFormTab(tabKey) {
    setRoleFormTabs(prev => prev.includes(tabKey) ? prev.filter(t => t !== tabKey) : [...prev, tabKey]);
  }

  async function handleRoleSave(e) {
    e.preventDefault();
    setRoleSaving(true);
    setRoleError('');
    try {
      const res = await fetch('/api/admin/roles', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: roleFormEditing || roleFormName, label: roleFormLabel, tabs: roleFormTabs, color: roleFormColor })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setRoles(data.roles);
        openNewRoleForm();
        setSuccessMsg(`Role "${roleFormLabel}" saved.`);
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setRoleError(data.error || 'Could not save role.');
      }
    } catch (err) {
      setRoleError('Network error.');
    } finally {
      setRoleSaving(false);
    }
  }

  async function confirmDeleteRole() {
    if (!roleDeleteConfirm) return;
    try {
      const res = await fetch(`/api/admin/roles?name=${encodeURIComponent(roleDeleteConfirm)}`, { method: 'DELETE' });
      const data = await res.json();
      if (res.ok && data.success) {
        setRoles(data.roles);
        if (roleFormEditing === roleDeleteConfirm) openNewRoleForm();
      } else {
        setRoleError(data.error || 'Could not delete role.');
      }
    } catch (err) {
      setRoleError('Network error.');
    } finally {
      setRoleDeleteConfirm(null);
    }
  }

  useEffect(() => {
    if (user) fetchMyPermissions();
    else setMyPermissions({ tabs: [], isStaff: false, isAdmin: false });
  }, [user]);

  useEffect(() => {
    if (!myPermissions?.isStaff) return;
    fetchData();
    fetchCarouselSets();
    fetchPolls();
    fetchEvents();
    fetchUpdates();
    fetchUpdateCategories();
    fetchNewsPosts();
    fetchNewsTopics();
    fetchGuide();
    fetchRules();
    fetchTips();
    fetchVoteSites();
    fetchReports();
    fetchBugReports();
    fetchAccessList();
    fetchSettings();
    fetchWiki();
    fetchProjects();
    if (myPermissions.tabs.includes('console')) fetchConsoleLog();
    if (myPermissions.tabs.includes('applications')) fetchStaffApplications();
    if (myPermissions.isAdmin) fetchRoles();
  }, [myPermissions]);

  const handleOpenActionModal = (targetUser, type) => {
    setActionTargetUsername(targetUser);
    setActionType(type);
    setActionReason('');
    setActionError('');
    setActionSuccess('');
    setActionModalOpen(true);
  };

  const handleModerationAction = async (e) => {
    e.preventDefault();
    if (!actionTargetUsername || !actionReason.trim()) return;
    setActionSubmitting(true);
    setActionError('');
    setActionSuccess('');

    try {
      const res = await fetch('/api/moderation', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          targetUsername: actionTargetUsername,
          type: actionType,
          reason: actionReason,
          staffMember: user.mcUsername,
          role: user.role
        })
      });

      const data = await res.json();
      if (res.ok && data.success) {
        if (data.rconBypassed) {
          setActionSuccess(`Logged ${actionType} for ${actionTargetUsername} (RCON not configured on this environment — no in-game command was sent).`);
        } else if (data.rconMessage !== null && data.rconMessage !== undefined) {
          setActionSuccess(`${actionType.toUpperCase()} dispatched for ${actionTargetUsername}. Server replied: "${data.rconMessage || '(empty response)'}"`);
        } else {
          setActionSuccess(`Successfully logged ${actionType} action for ${actionTargetUsername}!`);
        }
        if (data.logs) setLogs(data.logs);

        setTimeout(() => {
          setActionModalOpen(false);
          setActionReason('');
          setActionSuccess('');
        }, 1500);
      } else {
        setActionError(data.error || `Failed to submit action`);
      }
    } catch {
      setActionError("Failed to connect to moderation transmitter.");
    } finally {
      setActionSubmitting(false);
    }
  };

  async function fetchData() {
    try {
      const res = await fetch(`/api/moderation?role=${user?.role}`);
      const data = await res.json();
      if (res.ok) {
        setUsers(data.users || []);
        setPendingRegistrations(data.pendingRegistrations || []);
        setLogs(data.logs || []);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }

  async function confirmGdprDelete() {
    if (!gdprDeleteConfirm) return;
    setGdprDeleting(true);
    setGdprError('');
    try {
      const res = await fetch('/api/admin/gdpr-delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetEmail: gdprDeleteConfirm.email })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setSuccessMsg(`Account for ${gdprDeleteConfirm.mcUsername || gdprDeleteConfirm.email} permanently deleted.`);
        setTimeout(() => setSuccessMsg(''), 3000);
        setGdprDeleteConfirm(null);
        await fetchData();
      } else {
        setGdprError(data.error || 'Could not delete account.');
      }
    } catch (err) {
      setGdprError('Network error.');
    } finally {
      setGdprDeleting(false);
    }
  }

  async function fetchCarouselSets() {
    try {
      const res = await fetch('/api/carousel/sets');
      const data = await res.json();
      if (res.ok && data.sets) {
        setCarouselSets(data.sets);
        // Auto-select the active set (or just the first one) so the manager
        // isn't blank on first open — but don't clobber a set the staff
        // member already has open.
        setSelectedCarouselSetId(prev => {
          if (prev && data.sets.some(s => s.id === prev)) return prev;
          const active = data.sets.find(s => s.isActive);
          return (active || data.sets[0])?.id || null;
        });
      }
    } catch (err) {
      console.error("Failed to load carousel sets", err);
    }
  }

  async function fetchCarouselItemsForSet(setId) {
    if (!setId) { setCarouselItems([]); return; }
    setLoadingCarouselItems(true);
    try {
      const res = await fetch(`/api/carousel?setId=${encodeURIComponent(setId)}`);
      const data = await res.json();
      if (res.ok && data.items) {
        setCarouselItems(data.items);
      }
    } catch (err) {
      console.error("Failed to load carousel items for set", err);
    } finally {
      setLoadingCarouselItems(false);
    }
  }

  // Drag-reorder of slides within the selected set — same
  // grab-drop-splice-persist pattern as the wiki index's handleDrop.
  async function persistSlideOrder(orderedIds) {
    try {
      const res = await fetch('/api/carousel', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'reorder', orderedIds, setId: selectedCarouselSetId })
      });
      const data = await res.json();
      if (data.items) setCarouselItems(data.items);
    } catch (err) {
      console.error('Failed to save new slide order:', err);
    }
  }

  function handleSlideDrop(targetId) {
    if (!draggedSlideId || draggedSlideId === targetId) { setDraggedSlideId(null); return; }
    const ids = carouselItems.map(s => s.id);
    const from = ids.indexOf(draggedSlideId);
    const to = ids.indexOf(targetId);
    setDraggedSlideId(null);
    if (from === -1 || to === -1) return;
    const newOrder = [...ids];
    newOrder.splice(from, 1);
    newOrder.splice(to, 0, draggedSlideId);

    // Optimistic local reorder so the drag feels instant.
    setCarouselItems(prev => {
      const byId = new Map(prev.map(s => [s.id, s]));
      return newOrder.map(id => byId.get(id)).filter(Boolean);
    });

    persistSlideOrder(newOrder);
  }

  // Load the selected set's images whenever the selection changes, and reset
  // the per-image form so a stale edit doesn't leak into a different set.
  useEffect(() => {
    fetchCarouselItemsForSet(selectedCarouselSetId);
    setEditingSlideId(null);
    setSlideTitle('');
    setSlideDesc('');
    setSlideImageSrc('');
    setSlideSetIds(selectedCarouselSetId ? [selectedCarouselSetId] : []);
  }, [selectedCarouselSetId]);

  const handleCreateSet = async (e) => {
    e.preventDefault();
    if (!newSetName.trim()) return;
    setCreatingSet(true);
    setCarouselSetError('');
    try {
      const res = await fetch('/api/carousel/sets', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newSetName.trim(), role: user.role })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setCarouselSets(data.sets);
        setNewSetName('');
        // The newly created set is whichever one we didn't already know about.
        const knownIds = new Set(carouselSets.map(s => s.id));
        const created = data.sets.find(s => !knownIds.has(s.id));
        if (created) setSelectedCarouselSetId(created.id);
      } else {
        setCarouselSetError(data.error || "Failed to create set");
      }
    } catch (err) {
      setCarouselSetError("Network error while creating set");
    } finally {
      setCreatingSet(false);
    }
  };

  const handleRenameSet = async (id) => {
    if (!renameSetName.trim()) { setRenamingSetId(null); return; }
    setCarouselSetError('');
    try {
      const res = await fetch('/api/carousel/sets', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id, name: renameSetName.trim(), role: user.role })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setCarouselSets(data.sets);
      } else {
        setCarouselSetError(data.error || "Failed to rename set");
      }
    } catch (err) {
      setCarouselSetError("Network error while renaming set");
    } finally {
      setRenamingSetId(null);
    }
  };

  const handleActivateSet = async (id) => {
    setCarouselSetError('');
    try {
      const res = await fetch('/api/carousel/sets', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id, activate: true, role: user.role })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setCarouselSets(data.sets);
      } else {
        setCarouselSetError(data.error || "Failed to activate set");
      }
    } catch (err) {
      setCarouselSetError("Network error while activating set");
    }
  };

  const handleDeleteSet = async (id) => {
    if (!confirm("Delete this entire set and every screenshot in it? This cannot be undone.")) return;
    setCarouselSetError('');
    try {
      const res = await fetch(`/api/carousel/sets?id=${id}&role=${user.role}`, { method: 'DELETE' });
      const data = await res.json();
      if (res.ok && data.success) {
        setCarouselSets(data.sets);
        if (selectedCarouselSetId === id) {
          setSelectedCarouselSetId((data.sets.find(s => s.isActive) || data.sets[0])?.id || null);
        }
      } else {
        setCarouselSetError(data.error || "Failed to delete set");
      }
    } catch (err) {
      setCarouselSetError("Network error while deleting set");
    }
  };

  async function fetchInactiveCandidates() {
    setLoadingInactive(true);
    setInactiveLoadError('');
    setPurgeResults(null);
    try {
      const res = await fetch('/api/admin/inactive-accounts');
      const data = await res.json();
      if (res.ok) {
        setInactiveCandidates(data.candidates || []);
        setInactivityDays(data.inactivityDays || 90);
        setSelectedInactiveEmails([]);
      } else {
        setInactiveLoadError(data.error || 'Failed to load inactive accounts');
      }
    } catch (err) {
      setInactiveLoadError('Network error while loading inactive accounts');
    } finally {
      setLoadingInactive(false);
    }
  }

  async function fetchNewsletterHistory() {
    setLoadingNewsletterHistory(true);
    try {
      const res = await fetch('/api/admin/newsletter');
      const data = await res.json();
      if (res.ok) setNewsletterHistory(data.history || []);
    } catch {
      // Silent — the compose form still works even if history fails to load.
    } finally {
      setLoadingNewsletterHistory(false);
    }
  }

  async function sendNewsletter() {
    setSendingNewsletter(true);
    setNewsletterError('');
    setNewsletterResult(null);
    try {
      const res = await fetch('/api/admin/newsletter', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ subject: newsletterSubject, body: newsletterBody })
      });
      const data = await res.json();
      if (res.ok) {
        setNewsletterResult(data);
        setNewsletterHistory(data.history || []);
        setNewsletterSubject('');
        setNewsletterBody('');
        setNewsletterConfirming(false);
      } else {
        setNewsletterError(data.error || 'Failed to send newsletter');
      }
    } catch {
      setNewsletterError('Network error');
    } finally {
      setSendingNewsletter(false);
    }
  }

  async function fetchAdminFiles() {
    setLoadingAdminFiles(true);
    try {
      const res = await fetch('/api/admin/files');
      const data = await res.json();
      if (res.ok) setAdminFiles(data.files || []);
    } catch {
      // Silent — same as newsletter history above.
    } finally {
      setLoadingAdminFiles(false);
    }
  }

  async function handleAdminFileUpload(e) {
    e.preventDefault();
    if (!adminFileToUpload) return;
    setAdminFileError('');
    setUploadingAdminFile(true);
    try {
      const formData = new FormData();
      formData.append('file', adminFileToUpload);
      if (adminFileDescription.trim()) formData.append('description', adminFileDescription.trim());

      const res = await fetch('/api/admin/files', { method: 'POST', body: formData });
      const data = await res.json();
      if (res.ok && data.success) {
        setAdminFiles(data.files);
        setAdminFileToUpload(null);
        setAdminFileDescription('');
        const fileInput = document.getElementById('admin-file-upload-input');
        if (fileInput) fileInput.value = '';
      } else {
        setAdminFileError(data.error || 'Failed to upload file');
      }
    } catch {
      setAdminFileError('Network error');
    } finally {
      setUploadingAdminFile(false);
    }
  }

  async function handleAdminFileDelete(id, name) {
    if (!confirm(`Delete "${name}"? This can't be undone.`)) return;
    setAdminFileError('');
    try {
      const res = await fetch(`/api/admin/files?id=${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (res.ok && data.success) {
        setAdminFiles(data.files);
      } else {
        setAdminFileError(data.error || 'Failed to delete file');
      }
    } catch {
      setAdminFileError('Network error');
    }
  }

  const formatFileSize = (bytes) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  const toggleInactiveSelection = (email) => {
    setSelectedInactiveEmails(prev =>
      prev.includes(email) ? prev.filter(e => e !== email) : [...prev, email]
    );
  };

  const toggleSelectAllInactive = () => {
    setSelectedInactiveEmails(prev =>
      prev.length === inactiveCandidates.length ? [] : inactiveCandidates.map(c => c.email)
    );
  };

  const handlePurgeInactive = async () => {
    if (selectedInactiveEmails.length === 0) return;
    if (!confirm(`Permanently delete ${selectedInactiveEmails.length} account(s) and remove them from the whitelist? This cannot be undone — the player will need to register again to return.`)) return;

    setPurgingInactive(true);
    setPurgeResults(null);
    try {
      const res = await fetch('/api/admin/inactive-accounts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ emails: selectedInactiveEmails })
      });
      const data = await res.json();
      if (res.ok) {
        setPurgeResults(data.results || []);
        const purgedEmails = new Set((data.results || []).filter(r => r.success).map(r => r.email));
        setInactiveCandidates(prev => prev.filter(c => !purgedEmails.has(c.email)));
        setSelectedInactiveEmails([]);
      } else {
        setInactiveLoadError(data.error || 'Failed to purge accounts');
      }
    } catch (err) {
      setInactiveLoadError('Network error while purging accounts');
    } finally {
      setPurgingInactive(false);
    }
  };

  async function fetchPolls() {
    try {
      const res = await fetch('/api/polls');
      const data = await res.json();
      if (res.ok && data.polls) {
        setPolls(data.polls);
      }
    } catch (err) {
      console.error("Failed to load polls API", err);
    }
  }

  async function fetchUpdates() {
    try {
      const res = await fetch('/api/updates');
      const data = await res.json();
      if (res.ok && data.updates) {
        setUpdates(data.updates);
      }
    } catch (err) {
      console.error("Failed to load updates API", err);
    } finally {
      setLoadingUpdates(false);
    }
  }

  async function fetchUpdateCategories() {
    try {
      const res = await fetch('/api/updates/categories');
      const data = await res.json();
      if (res.ok && data.categories) {
        setUpdateCategories(data.categories);
        if (data.categories.length > 0) {
          setUpdateCategory(data.categories[0]);
        }
      }
    } catch (err) {
      console.error("Failed to load updates categories API", err);
    }
  }

  async function fetchNewsPosts() {
    try {
      const res = await fetch('/api/news');
      const data = await res.json();
      if (res.ok && data.posts) {
        setNewsPosts(data.posts);
      }
    } catch (err) {
      console.error("Failed to load news API", err);
    } finally {
      setLoadingNews(false);
    }
  }

  async function fetchNewsTopics() {
    try {
      const res = await fetch('/api/news/topics');
      const data = await res.json();
      if (res.ok && data.topics) {
        setNewsTopics(data.topics);
        if (data.topics.length > 0) {
          setNewsTopic(data.topics[0]);
        }
      }
    } catch (err) {
      console.error("Failed to load news topics API", err);
    }
  }

  async function fetchGuide() {
    try {
      const res = await fetch('/api/guide');
      const data = await res.json();
      if (res.ok && data.guide) setGuideSteps(data.guide);
    } catch (err) { console.error("Failed to load guide", err); }
  }

  async function fetchRules() {
    try {
      const res = await fetch('/api/rules');
      const data = await res.json();
      if (res.ok && data.rules) setRules(data.rules);
    } catch (err) { console.error("Failed to load rules", err); }
  }

  async function fetchTips() {
    try {
      const res = await fetch('/api/tips');
      const data = await res.json();
      if (res.ok && data.tips) setTips(data.tips);
    } catch (err) { console.error("Failed to load tips", err); }
  }

  async function fetchVoteSites() {
    try {
      const res = await fetch('/api/vote/sites');
      const data = await res.json();
      if (res.ok && data.sites) setVoteSites(data.sites);
    } catch (err) { console.error("Failed to load vote sites", err); }
  }

  async function fetchReports() {
    try {
      const res = await fetch('/api/reports');
      const data = await res.json();
      if (res.ok && data.reports) setReports(data.reports);
    } catch (err) { console.error("Failed to load reports", err); }
  }

  async function fetchBugReports() {
    try {
      const res = await fetch('/api/bug-reports');
      const data = await res.json();
      if (res.ok && data.reports) setBugReports(data.reports);
    } catch (err) { console.error("Failed to load bug reports", err); }
  }

  async function fetchStaffApplications() {
    try {
      const res = await fetch('/api/staff-applications');
      const data = await res.json();
      if (res.ok && data.applications) setStaffApplications(data.applications);
    } catch (err) { console.error("Failed to load staff applications", err); }
  }

  async function saveApplicationNotes(id) {
    try {
      const res = await fetch('/api/staff-applications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'update_notes', id, staffNotes: saNotesDrafts[id] ?? '' })
      });
      const d = await res.json();
      if (d.applications) setStaffApplications(d.applications);
    } catch (err) { console.error("Failed to save staff application notes", err); }
  }

  async function decideApplication(id, status) {
    if (!confirm(status === 'approved' ? 'Approve this application?' : 'Decline this application?')) return;
    setSaDecidingId(id);
    try {
      const res = await fetch('/api/staff-applications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'decide', id, status, staffNotes: saNotesDrafts[id] })
      });
      const d = await res.json();
      if (d.applications) setStaffApplications(d.applications);
      else if (d.error) alert(d.error);
    } catch (err) {
      console.error("Failed to decide on staff application", err);
    } finally {
      setSaDecidingId(null);
    }
  }

  async function handleApplicationContactSubmit(e) {
    e.preventDefault();
    if (!saContactTarget || !saContactMessage.trim()) return;
    setSaContactSending(true);
    setSaContactError('');
    try {
      const res = await fetch('/api/staff-applications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'contact', id: saContactTarget.id, channel: saContactChannel, message: saContactMessage.trim() })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setSaContactTarget(null);
        setSaContactMessage('');
      } else {
        setSaContactError(data.error || 'Failed to send');
      }
    } catch {
      setSaContactError('Network error. Please try again.');
    } finally {
      setSaContactSending(false);
    }
  }

  async function toggleBugThread(id) {
    if (expandedBugId === id) { setExpandedBugId(null); return; }
    setExpandedBugId(id);
    if (!bugThreads[id]) {
      try {
        const res = await fetch(`/api/bug-reports?messagesFor=${encodeURIComponent(id)}`);
        const data = await res.json();
        if (res.ok && data.messages) setBugThreads(prev => ({ ...prev, [id]: data.messages }));
      } catch (err) { console.error("Failed to load bug report thread", err); }
    }
  }

  async function saveBugNotes(id) {
    setBugNotesSavingId(id);
    try {
      const res = await fetch('/api/bug-reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'update_notes', id, staffNotes: bugNotesDrafts[id] ?? '' })
      });
      const d = await res.json();
      if (d.reports) setBugReports(d.reports);
    } catch (err) { console.error("Failed to save bug report notes", err); }
    finally { setBugNotesSavingId(null); }
  }

  // Re-categorizing a report — mainly for ones filed as "Other" before a
  // matching predefined category existed (see bug-categories.js).
  async function saveBugCategory(id, category, customCategory) {
    setBugCategorySavingId(id);
    try {
      const res = await fetch('/api/bug-reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'update_category', id, category, customCategory })
      });
      const d = await res.json();
      if (d.reports) {
        setBugReports(d.reports);
        setBugCategoryDrafts(prev => { const next = { ...prev }; delete next[id]; return next; });
      }
    } catch (err) { console.error("Failed to save bug report category", err); }
    finally { setBugCategorySavingId(null); }
  }

  async function handleContactSubmit(e) {
    e.preventDefault();
    if (!contactTarget || !contactMessage.trim()) return;
    setContactSending(true);
    setContactError('');
    try {
      const res = await fetch('/api/bug-reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'contact', id: contactTarget.id, channel: contactChannel, message: contactMessage.trim() })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setBugThreads(prev => ({ ...prev, [contactTarget.id]: data.messages }));
        setContactTarget(null);
        setContactMessage('');
        setExpandedBugId(contactTarget.id);
      } else {
        setContactError(data.error || 'Failed to send');
      }
    } catch {
      setContactError('Network error. Please try again.');
    } finally {
      setContactSending(false);
    }
  }

  async function handleMarkDuplicate(e) {
    e.preventDefault();
    if (!duplicateTarget || !duplicateOfId) return;
    const res = await fetch('/api/bug-reports', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'update_status', id: duplicateTarget.id, status: 'duplicate', duplicateOf: duplicateOfId })
    });
    const d = await res.json();
    if (d.reports) setBugReports(d.reports);
    setDuplicateTarget(null);
    setDuplicateOfId('');
  }

  async function fetchAccessList() {
    try {
      const res = await fetch('/api/admin/access-list');
      const data = await res.json();
      if (res.ok) setAccessList({ banned: data.banned || [], whitelistedUnregistered: data.whitelistedUnregistered || [], whitelistedTotal: data.whitelistedTotal || 0 });
    } catch (err) { console.error("Failed to load access list", err); }
  }

  async function fetchConsoleLog() {
    try {
      const res = await fetch('/api/admin/rcon');
      const data = await res.json();
      if (res.ok && data.log) setConsoleLog(data.log);
    } catch (err) { console.error("Failed to load RCON console log", err); }
  }

  async function handleConsoleSend(e) {
    e.preventDefault();
    if (!consoleInput.trim() || consoleSending) return;
    setConsoleSending(true);
    setConsoleError('');
    try {
      const res = await fetch('/api/admin/rcon', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ command: consoleInput.trim() })
      });
      const data = await res.json();
      if (!res.ok) {
        setConsoleError(data.error || 'Command failed');
      } else {
        setConsoleInput('');
        if (data.log) setConsoleLog(data.log);
      }
    } catch (err) {
      setConsoleError('Could not reach the server.');
    } finally {
      setConsoleSending(false);
    }
  }

  async function fetchSettings() {
    try {
      const res = await fetch('/api/settings');
      const data = await res.json();
      if (res.ok && data.settings) {
        setSettingsJavaVersion(data.settings.java_version || '');
        setSettingsMapUrl(data.settings.map_url || '');
        setSettingsServerAddress(data.settings.server_address || '');
      }
    } catch (err) { console.error("Failed to load settings", err); }
  }

  async function fetchEvents() {
    try {
      const res = await fetch('/api/events');
      const data = await res.json();
      if (res.ok) {
        setEvents(data.events || []);
        setRegistrations(data.registrations || []);
      }
    } catch (err) {
      console.error("Failed to load events", err);
    } finally {
      setLoadingEvents(false);
    }
  }

  async function fetchWiki() {
    try {
      const res = await fetch('/api/wiki');
      const data = await res.json();
      if (res.ok && data.pages) {
        setWikiPages(data.pages);
      }
    } catch (err) {
      console.error("Failed to load wiki API", err);
    } finally {
      setLoadingWiki(false);
    }
  }

  const handleWikiImageUpload = (e) => {
    const files = Array.from(e.target.files);
    if (files.length === 0) return;

    setWikiImageError('');

    files.forEach(file => {
      if (!file.type.startsWith('image/')) {
        setWikiImageError(prev => (prev ? prev + "\n" : "") + `File "${file.name}" is not an image.`);
        return;
      }

      const tempId = 'img-' + Math.random().toString(36).substr(2, 9);
      setWikiUploadedImages(prev => [...prev, { id: tempId, name: file.name, src: '', isProcessing: true }]);

      const reader = new FileReader();
      reader.onload = (event) => {
        if (file.type === 'image/gif') {
          setWikiUploadedImages(prev => prev.map(img => img.id === tempId ? { ...img, src: event.target.result, isProcessing: false } : img));
          return;
        }

        const img = new Image();
        img.onload = () => {
          const canvas = document.createElement('canvas');
          const ctx = canvas.getContext('2d');

          const MAX_WIDTH = 1000;
          const MAX_HEIGHT = 600;
          let width = img.width;
          let height = img.height;

          if (width > height) {
            if (width > MAX_WIDTH) {
              height *= MAX_WIDTH / width;
              width = MAX_WIDTH;
            }
          } else {
            if (height > MAX_HEIGHT) {
              width *= MAX_HEIGHT / height;
              height = MAX_HEIGHT;
            }
          }

          canvas.width = width;
          canvas.height = height;
          ctx.drawImage(img, 0, 0, width, height);

          const base64Data = canvas.toDataURL('image/jpeg', 0.85);
          setWikiUploadedImages(prev => prev.map(img => img.id === tempId ? { ...img, src: base64Data, isProcessing: false } : img));
        };
        img.onerror = () => {
          setWikiUploadedImages(prev => prev.filter(img => img.id !== tempId));
          setWikiImageError(prev => (prev ? prev + "\n" : "") + `Failed to load image "${file.name}"`);
        };
        img.src = event.target.result;
      };
      reader.readAsDataURL(file);
    });
    e.target.value = '';
  };

  // Real file attachments (PDFs/screenshots) — only available once a page
  // has actually been saved (needs a real id to attach to), so this only
  // shows up while editing an existing page, not while composing a new one.
  const handleWikiAttachmentUpload = async (e) => {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file || !wikiEditId) return;
    setWikiAttachmentUploading(true);
    setWikiAttachmentError('');
    try {
      const formData = new FormData();
      formData.append('id', wikiEditId);
      formData.append('action', 'add_attachment');
      formData.append('role', user?.role);
      formData.append('file', file);
      const res = await fetch('/api/wiki', { method: 'POST', body: formData });
      const data = await res.json();
      if (res.ok && data.success) {
        setWikiPages(data.pages);
      } else {
        setWikiAttachmentError(data.error || 'Failed to upload attachment');
      }
    } catch (err) {
      setWikiAttachmentError('Network error while uploading attachment');
    } finally {
      setWikiAttachmentUploading(false);
    }
  };

  const handleWikiAttachmentRemove = async (url) => {
    if (!wikiEditId || !confirm('Remove this attachment?')) return;
    setWikiAttachmentError('');
    try {
      const formData = new FormData();
      formData.append('id', wikiEditId);
      formData.append('action', 'remove_attachment');
      formData.append('role', user?.role);
      formData.append('url', url);
      const res = await fetch('/api/wiki', { method: 'POST', body: formData });
      const data = await res.json();
      if (res.ok && data.success) {
        setWikiPages(data.pages);
      } else {
        setWikiAttachmentError(data.error || 'Failed to remove attachment');
      }
    } catch (err) {
      setWikiAttachmentError('Network error while removing attachment');
    }
  };

  const handleWikiSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    const finalCategory = isWikiCustomCategory ? wikiCustomCategory.trim() : wikiCategory;
    if (!wikiTitle || !finalCategory || !wikiContent) {
      setError("Title, category and content are required.");
      return;
    }

    try {
      const res = await fetch('/api/wiki', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: wikiEditId || undefined,
          title: wikiTitle,
          category: finalCategory,
          content: wikiContent,
          parentId: wikiParentId || null,
          updatedBy: user?.mcUsername || "Staff",
          role: user?.role
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setSuccessMsg(wikiEditId ? "Wiki article calibrated successfully!" : "New wiki article transcribed!");
        setWikiEditId(null);
        setWikiTitle('');
        setWikiContent('');
        setWikiParentId('');
        setIsWikiCustomCategory(false);
        setWikiCustomCategory('');
        setWikiUploadedImages([]);
        setWikiImageError('');
        setWikiAttachmentError('');
        await fetchWiki();
        setSelectedWikiPage(null);
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to save wiki page");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  // Opens the confirm modal instead of deleting immediately — see
  // wikiDeleteConfirm's declaration for why.
  const handleWikiDelete = (page) => {
    setWikiDeleteConfirm(page);
  };

  const confirmDeleteWikiPage = async () => {
    if (!wikiDeleteConfirm) return;
    const id = wikiDeleteConfirm.id;
    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch(`/api/wiki?id=${id}&role=${user?.role}`, {
        method: 'DELETE'
      });
      if (res.ok) {
        setSuccessMsg("Wiki page purged successfully!");
        if (selectedWikiPage?.id === id) {
          setSelectedWikiPage(null);
        }
        await fetchWiki();
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        const data = await res.json();
        setError(data.error || "Failed to delete");
      }
    } catch (err) {
      setError("Network error");
    } finally {
      setWikiDeleteConfirm(null);
    }
  };

  // Deletes every page inside a category in one go — there was previously no
  // way to remove a whole category short of editing each page's category
  // field one by one, which is exactly the "unclear" deletion staff flagged.
  const handleDeleteWikiCategory = async () => {
    if (!wikiCategoryDeleteConfirm) return;
    setError('');
    setSuccessMsg('');
    setWikiCategoryDeleting(true);

    try {
      const res = await fetch(`/api/wiki?category=${encodeURIComponent(wikiCategoryDeleteConfirm)}&role=${user?.role}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setSuccessMsg(`Category "${wikiCategoryDeleteConfirm}" and its ${data.count} page(s) purged successfully!`);
        if (selectedWikiPage?.category === wikiCategoryDeleteConfirm) {
          setSelectedWikiPage(null);
        }
        if (wikiCategory === wikiCategoryDeleteConfirm) {
          setWikiCategory('');
        }
        setWikiPages(data.pages || []);
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to delete category");
      }
    } catch (err) {
      setError("Network error");
    } finally {
      setWikiCategoryDeleting(false);
      setWikiCategoryDeleteConfirm(null);
    }
  };

  const handleRenameWikiCategory = async (oldName, newNameRaw) => {
    const newName = (newNameRaw || '').trim();
    if (!newName || newName === oldName) { setWikiCategoryRenameEdit(null); return; }
    setError('');
    setSuccessMsg('');
    setWikiCategoryRenaming(true);

    try {
      const res = await fetch('/api/wiki', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'rename_category', role: user?.role, oldCategory: oldName, newCategory: newName })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setSuccessMsg(`Renamed "${oldName}" to "${newName}" (${data.count} page(s)).`);
        if (wikiCategory === oldName) setWikiCategory(newName);
        setWikiPages(data.pages || []);
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to rename category");
      }
    } catch (err) {
      setError("Network error");
    } finally {
      setWikiCategoryRenaming(false);
      setWikiCategoryRenameEdit(null);
    }
  };

  // Legacy "00: Foo" / "01: Bar" style category names — a hand-rolled way to
  // force display order before staff could actually drag-reorder chapters.
  // That's obsolete now, so this strips the leading "NN: " from every
  // category that still has one, in one confirmed batch.
  const WIKI_NUMBERED_CATEGORY_RE = /^\s*\d+\s*:\s*/;
  const wikiNumberedCategories = wikiCategories.filter(cat => WIKI_NUMBERED_CATEGORY_RE.test(cat));

  const handleStripCategoryNumbering = async () => {
    if (wikiNumberedCategories.length === 0) return;
    const pairs = wikiNumberedCategories.map(cat => [cat, cat.replace(WIKI_NUMBERED_CATEGORY_RE, '').trim()]);
    const preview = pairs.map(([o, n]) => `${o}  ->  ${n}`).join('\n');
    if (!confirm(`Strip numbering from ${pairs.length} categories?\n\n${preview}`)) return;

    setError('');
    setSuccessMsg('');
    setWikiStripNumberingRunning(true);
    try {
      let latestPages = wikiPages;
      for (const [oldName, newName] of pairs) {
        const res = await fetch('/api/wiki', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action: 'rename_category', role: user?.role, oldCategory: oldName, newCategory: newName })
        });
        const data = await res.json();
        if (!res.ok || !data.success) {
          setError(`Stopped after "${oldName}": ${data.error || 'unknown error'}`);
          setWikiPages(latestPages);
          return;
        }
        latestPages = data.pages || latestPages;
      }
      setWikiPages(latestPages);
      setSuccessMsg(`Stripped numbering from ${pairs.length} categories.`);
      setTimeout(() => setSuccessMsg(''), 3000);
    } catch (err) {
      setError("Network error");
    } finally {
      setWikiStripNumberingRunning(false);
    }
  };

  const handleWikiRestoreRevision = async (pageId, revisionIdx) => {
    if (!confirm("Are you sure you want to roll back this page to this revision?")) return;
    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch('/api/wiki', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: 'restore_revision',
          id: pageId,
          revisionIndex: revisionIdx,
          role: user?.role
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setSuccessMsg("Revision restored successfully!");
        setWikiPreviewRevisionContent(null);
        setWikiActiveViewTab('article');
        await fetchWiki();
        if (selectedWikiPage?.id === pageId) {
          const match = data.page || wikiPages.find(p => p.id === pageId);
          setSelectedWikiPage(match);
        }
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to restore revision");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const resetEventForm = () => {
    setEventEditId(null);
    setEventTitle('');
    setEventDesc('');
    setEventDate('');
    setEventTime('');
    setEventTimezone('UTC+2');
    setEventImageFile(null);
    setEventImagePreview('');
    setEventExistingImage('');
    setEventRemoveImage(false);
    const fileInput = document.getElementById('event-poster-file');
    if (fileInput) fileInput.value = '';
  };

  const handleEventImageSelect = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setEventImageFile(file);
    setEventRemoveImage(false);
    setEventImagePreview(URL.createObjectURL(file));
  };

  const handleEventSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    try {
      const formData = new FormData();
      if (eventEditId) formData.append('id', eventEditId);
      formData.append('title', eventTitle);
      formData.append('description', eventDesc);
      formData.append('date', eventDate);
      formData.append('time', eventTime);
      formData.append('timezone', eventTimezone);
      formData.append('role', user?.role || '');
      if (eventImageFile) formData.append('file', eventImageFile);
      if (eventRemoveImage) formData.append('removeImage', 'true');

      const res = await fetch('/api/events', { method: 'POST', body: formData });
      const data = await res.json();
      if (res.ok && data.success) {
        setSuccessMsg(eventEditId ? "Event rescheduled successfully!" : "New community event scheduled!");
        resetEventForm();
        await fetchEvents();
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to save event");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleEventDelete = async (id) => {
    if (!confirm("Are you sure you want to delete this event?")) return;
    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch(`/api/events?id=${id}&role=${user?.role}`, {
        method: 'DELETE'
      });
      if (res.ok) {
        setSuccessMsg("Event purged from calendar!");
        await fetchEvents();
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        const data = await res.json();
        setError(data.error || "Failed to delete event");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleStaffRegisterToggle = async (eventId, mcUsername, currentlyRegistered) => {
    setError('');
    setSuccessMsg('');
    try {
      const action = currentlyRegistered ? 'unregister' : 'register';
      const res = await fetch('/api/events/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          eventId,
          username: mcUsername,
          action
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setRegistrations(data.registrations);
        setSuccessMsg(`Successfully ${currentlyRegistered ? 'unregistered' : 'registered'} ${mcUsername}`);
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to update RSVP");
      }
    } catch (err) {
      setError("Network error while updating RSVP");
    }
  };

  const handleAddOption = () => {
    setPollOptions([...pollOptions, '']);
  };

  const handleRemoveOption = (index) => {
    if (pollOptions.length <= 2) return;
    const newOpts = [...pollOptions];
    newOpts.splice(index, 1);
    setPollOptions(newOpts);
  };

  const handleOptionChange = (index, value) => {
    const newOpts = [...pollOptions];
    newOpts[index] = value;
    setPollOptions(newOpts);
  };

  const handlePollSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    const filteredOptions = pollOptions.map(opt => opt.trim()).filter(Boolean);
    if (!pollQuestion || filteredOptions.length < 2) {
      setError("Question and at least 2 options are required");
      return;
    }

    try {
      const res = await fetch('/api/polls', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: pollEditId || undefined,
          role: user.role,
          question: pollQuestion,
          options: filteredOptions
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setPolls(data.polls);
        setSuccessMsg(pollEditId ? "Poll updated successfully!" : "Poll created successfully!");
        setPollQuestion('');
        setPollOptions(['', '']);
        setPollEditId(null);
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to save poll");
      }
    } catch (err) {
      setError("Network error while saving poll");
    }
  };

  const handlePollEdit = (poll) => {
    setPollEditId(poll.id);
    setPollQuestion(poll.question);
    setPollOptions((poll.options || []).map(o => typeof o === 'string' ? o : o.text));
  };

  const handlePollDelete = async (pollId) => {
    if (!confirm("Are you sure you want to delete this poll?")) return;
    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch(`/api/polls?id=${pollId}&role=${user.role}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setPolls(data.polls);
        setSuccessMsg("Poll deleted successfully!");
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to delete poll");
      }
    } catch (err) {
      setError("Network error while deleting poll");
    }
  };

  const handleImageFileChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setImageError("Please upload an image file (PNG, JPG, WEBP, etc.)");
      return;
    }

    setImageError('');
    setIsProcessingImage(true);

    const reader = new FileReader();
    reader.onload = (event) => {
      const img = new Image();
      img.onload = () => {
        // Create canvas with uniform 1200x600 resolution
        const canvas = document.createElement('canvas');
        canvas.width = 1200;
        canvas.height = 600;
        const ctx = canvas.getContext('2d');

        const targetWidth = 1200;
        const targetHeight = 600;
        const imgRatio = img.width / img.height;
        const targetRatio = targetWidth / targetHeight;

        let drawWidth = img.width;
        let drawHeight = img.height;
        let offsetX = 0;
        let offsetY = 0;

        if (imgRatio > targetRatio) {
          drawWidth = img.height * targetRatio;
          offsetX = (img.width - drawWidth) / 2;
        } else {
          drawHeight = img.width / targetRatio;
          offsetY = (img.height - drawHeight) / 2;
        }

        ctx.drawImage(
          img,
          offsetX, offsetY, drawWidth, drawHeight,
          0, 0, targetWidth, targetHeight
        );

        // Convert to base64 JPEG format with 85% compression
        const base64Data = canvas.toDataURL('image/jpeg', 0.85);
        setSlideImageSrc(base64Data);
        setIsProcessingImage(false);
      };
      img.onerror = () => {
        setImageError("Failed to load image file");
        setIsProcessingImage(false);
      };
      img.src = event.target.result;
    };
    reader.readAsDataURL(file);
  };

  const handleCarouselSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (!slideTitle || !slideDesc || !slideImageSrc) {
      setError("Title, description, and image are required");
      return;
    }
    if (slideSetIds.length === 0) {
      setError("Pick at least one set for this slide");
      return;
    }

    try {
      const res = await fetch('/api/carousel', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          role: user.role,
          id: editingSlideId,
          title: slideTitle,
          desc: slideDesc,
          src: slideImageSrc,
          setIds: slideSetIds
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setCarouselItems(data.items);
        fetchCarouselSets(); // refresh sidebar item counts
        setSuccessMsg(editingSlideId ? "Carousel slide updated successfully!" : "New carousel slide added successfully!");
        // Reset form
        setEditingSlideId(null);
        setSlideTitle('');
        setSlideDesc('');
        setSlideImageSrc('');
        setSlideSetIds(selectedCarouselSetId ? [selectedCarouselSetId] : []);
        const fileInput = document.getElementById('slide-image-file');
        if (fileInput) fileInput.value = '';
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to save slide");
      }
    } catch (err) {
      setError("Network error while saving slide");
    }
  };

  const handleEditClick = async (slide) => {
    setEditingSlideId(slide.id);
    setSlideTitle(slide.title);
    setSlideDesc(slide.desc);
    setSlideImageSrc(slide.src);
    setError('');
    setSuccessMsg('');
    // Which other sets this slide already belongs to, so the checklist
    // reflects reality instead of just the set currently being viewed.
    setSlideSetIds(selectedCarouselSetId ? [selectedCarouselSetId] : []);
    try {
      const res = await fetch(`/api/carousel?itemId=${encodeURIComponent(slide.id)}`);
      const data = await res.json();
      if (res.ok && Array.isArray(data.setIds)) setSlideSetIds(data.setIds);
    } catch (err) {
      console.error("Failed to load this slide's set memberships", err);
    }
  };

  const handleDeleteClick = async (slideId, setCount = 1) => {
    const confirmMsg = setCount > 1
      ? `This slide is shared with ${setCount - 1} other set${setCount > 2 ? 's' : ''} — this only removes it from the set you're viewing, it'll stay in the others. Continue?`
      : "Are you sure you want to delete this slide?";
    if (!confirm(confirmMsg)) return;
    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch(`/api/carousel?id=${slideId}&role=${user.role}&setId=${selectedCarouselSetId}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setCarouselItems(data.items);
        fetchCarouselSets(); // refresh sidebar item counts
        setSuccessMsg("Carousel slide deleted successfully!");
        setTimeout(() => setSuccessMsg(''), 3000);
        if (editingSlideId === slideId) {
          setEditingSlideId(null);
          setSlideTitle('');
          setSlideDesc('');
          setSlideImageSrc('');
          setSlideSetIds(selectedCarouselSetId ? [selectedCarouselSetId] : []);
        }
      } else {
        setError(data.error || "Failed to delete slide");
      }
    } catch (err) {
      setError("Network error while deleting slide");
    }
  };

  const handleRoleChange = async (targetEmail, newRole) => {
    setError('');
    setSuccessMsg('');
    try {
      const res = await fetch('/api/moderation', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          role: user.role,
          targetEmail,
          newRole,
          action: 'update_profile'
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setUsers(data.users);
        setSuccessMsg(`Successfully changed player role to ${newRole}`);
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to update role");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleModerationSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    if (!targetUsername || !reason) {
      setError("Target username and reason are required");
      return;
    }

    try {
      const res = await fetch('/api/moderation', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          role: user.role,
          targetUsername,
          type,
          reason,
          staffMember: user.mcUsername
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setLogs(data.logs);
        if (data.rconBypassed) {
          setSuccessMsg(`Logged ${type} for ${targetUsername} (RCON not configured — no in-game command was sent).`);
        } else if (data.rconMessage !== null && data.rconMessage !== undefined) {
          setSuccessMsg(`${type.toUpperCase()} dispatched to ${targetUsername}. Server replied: "${data.rconMessage || '(empty response)'}"`);
        } else {
          setSuccessMsg(`Action logged successfully: ${type} issued to ${targetUsername}`);
        }
        setTargetUsername('');
        setReason('');
        setTimeout(() => setSuccessMsg(''), 4000);
      } else {
        setError(data.error || "Failed to log action");
      }
    } catch (err) {
      setError("Network error");
    }
  };
  const handleQuickSanctionConfirm = async (e) => {
    e.preventDefault();
    if (!quickSanctionTarget || !quickReason.trim()) return;

    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch('/api/moderation', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          role: user.role,
          targetUsername: quickSanctionTarget.username,
          type: quickSanctionTarget.type,
          reason: quickReason,
          staffMember: user.mcUsername
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setLogs(data.logs);
        if (data.rconBypassed) {
          setSuccessMsg(`Logged ${quickSanctionTarget.type} for ${quickSanctionTarget.username} (RCON not configured — no in-game command was sent).`);
        } else if (data.rconMessage !== null && data.rconMessage !== undefined) {
          setSuccessMsg(`${quickSanctionTarget.type.toUpperCase()} dispatched to ${quickSanctionTarget.username}. Server replied: "${data.rconMessage || '(empty response)'}"`);
        } else {
          setSuccessMsg(`Action logged successfully: ${quickSanctionTarget.type} issued to ${quickSanctionTarget.username}`);
        }
        setQuickSanctionTarget(null);
        setQuickReason('');
        setTimeout(() => setSuccessMsg(''), 4000);
      } else {
        setError(data.error || "Failed to log action");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleUpdateSubmit = async (e) => {
    e.preventDefault();
    if (!updateTitle.trim() || !updateCategory.trim() || !updateContent.trim()) return;

    setError('');
    setSuccessMsg('');

    const payload = {
      id: updateId || undefined,
      title: updateTitle,
      category: updateCategory,
      content: updateContent,
      role: user.role,
      author: user.mcUsername
    };

    const method = updateId ? 'PUT' : 'POST';

    try {
      const res = await fetch('/api/updates', {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setUpdates(data.updates);
        setSuccessMsg(updateId ? "Update saved successfully" : "Update created successfully");
        setUpdateId('');
        setUpdateTitle('');
        setUpdateCategory('General');
        setUpdateContent('');
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to submit update");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleUpdateEdit = (update) => {
    setUpdateId(update.id);
    setUpdateTitle(update.title);
    setUpdateCategory(update.category);
    setUpdateContent(update.content);
  };

  const handleUpdateDelete = async (id) => {
    if (!confirm("Are you sure you want to delete this update transmission?")) return;
    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch(`/api/updates?id=${id}&role=${user.role}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setUpdates(data.updates);
        setSuccessMsg("Update deleted successfully");
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to delete update");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleCategoryCreateSubmit = async (e) => {
    e.preventDefault();
    if (!newCategoryName.trim()) return;

    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch('/api/updates/categories', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ category: newCategoryName.trim(), role: user.role })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setUpdateCategories(data.categories);
        setSuccessMsg(`Category "${newCategoryName}" created successfully`);
        setNewCategoryName('');
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to create category");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleCategoryDelete = async (catName) => {
    if (catName === 'General') {
      alert("General is the fallback category and cannot be deleted.");
      return;
    }
    if (!confirm(`Are you sure you want to delete the category "${catName}"? All updates under this category will automatically fallback to "General".`)) return;

    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch(`/api/updates/categories?category=${encodeURIComponent(catName)}&role=${user.role}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setUpdateCategories(data.categories);
        setUpdates(data.updates || []);
        setSuccessMsg(`Category "${catName}" deleted successfully`);
        if (updateCategory === catName) {
          setUpdateCategory('General');
        }
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to delete category");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleNewsSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');

    const payload = {
      id: newsPostId || undefined,
      title: newsTitle,
      type: newsType,
      topic: newsTopic,
      content: newsContent,
      link: newsLink.trim() || undefined,
      role: user.role,
      author: user.mcUsername
    };

    const method = newsPostId ? 'PUT' : 'POST';

    try {
      const res = await fetch('/api/news', {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setNewsPosts(data.posts);
        setSuccessMsg(newsPostId ? "Post saved successfully" : "Post published — followers of this topic have been notified");
        setNewsPostId('');
        setNewsTitle('');
        setNewsType('news');
        if (newsTopics.length > 0) setNewsTopic(newsTopics[0]);
        setNewsContent('');
        setNewsLink('');
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to submit news post");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleNewsEdit = (post) => {
    setNewsPostId(post.id);
    setNewsTitle(post.title);
    setNewsType(post.type);
    setNewsTopic(post.topic);
    setNewsContent(post.content);
    setNewsLink(post.link || '');
  };

  const handleNewsDelete = async (id) => {
    if (!confirm("Are you sure you want to delete this news post?")) return;
    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch(`/api/news?id=${id}&role=${user.role}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setNewsPosts(data.posts);
        setSuccessMsg("Post deleted successfully");
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to delete post");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleNewsTopicCreateSubmit = async (e) => {
    e.preventDefault();
    if (!newNewsTopicName.trim()) return;

    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch('/api/news/topics', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ topic: newNewsTopicName.trim(), role: user.role })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setNewsTopics(data.topics);
        setSuccessMsg(`Topic "${newNewsTopicName}" created successfully`);
        setNewNewsTopicName('');
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to create topic");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  const handleNewsTopicDelete = async (topicName) => {
    if (topicName === 'General') {
      alert("General is the fallback topic and cannot be deleted.");
      return;
    }
    if (!confirm(`Are you sure you want to delete the topic "${topicName}"? Its posts will automatically fallback to "General", and followers of it will lose that subscription.`)) return;

    setError('');
    setSuccessMsg('');

    try {
      const res = await fetch(`/api/news/topics?topic=${encodeURIComponent(topicName)}&role=${user.role}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setNewsTopics(data.topics);
        setNewsPosts(data.posts || []);
        setSuccessMsg(`Topic "${topicName}" deleted successfully`);
        if (newsTopic === topicName) {
          setNewsTopic('General');
        }
        setTimeout(() => setSuccessMsg(''), 3000);
      } else {
        setError(data.error || "Failed to delete topic");
      }
    } catch (err) {
      setError("Network error");
    }
  };

  // Auth Guard — a custom staff role (see Roles & Permissions in Settings)
  // is neither 'admin' nor 'moderator', so this can no longer be a plain
  // string check; it waits for myPermissions (fetched from the real,
  // server-verified /api/permissions/me) before deciding, so a legitimate
  // custom-role staffer doesn't flash "Access Denied" while that's loading.
  if (user && myPermissions === null) {
    return (
      <div className="max-w-md mx-auto my-12 text-center text-xs text-text-warm/40 italic py-12">
        Checking clearance...
      </div>
    );
  }
  if (!user || !myPermissions?.isStaff) {
    return (
      <div className="max-w-md mx-auto my-12 bg-sp-dark/95 border-2 border-crimson p-8 rounded-xl text-center steampunk-border shadow-2xl space-y-6">
        <div className="w-16 h-16 bg-crimson/15 rounded-full flex items-center justify-center border border-crimson text-rose-400 mx-auto">
          <ShieldAlert className="w-8 h-8" />
        </div>
        <h2 className="font-serif text-2xl font-bold text-rose-300 uppercase tracking-wider">Access Denied</h2>
        <p className="text-sm text-text-warm/85 leading-relaxed">
          Authorized personnel only. The steam command dashboard requires a staff role with at least one clearance granted.
        </p>
        <button
          onClick={() => setIsLoginOpen(true)}
          className="w-full py-2.5 rounded-lg text-xs font-semibold uppercase tracking-wider text-black bg-sp-gold hover:bg-white transition-all cursor-pointer font-serif border border-transparent shadow-md"
        >
          Authenticate Transmission
        </button>
      </div>
    );
  }

  // Link status per member: 'linked' (green, has a real account with a
  // verified mcUsername — the current registration flow only creates the
  // account after the in-game code checks out), 'pending' (orange, they
  // whitelisted + got a code sent but haven't verified it in-game yet — not
  // a real account, sourced from db.getAllPendingRegistrations), or
  // 'unlinked' (red, a real account with no mcUsername attached at all,
  // e.g. a staff-created account — never went through in-game linking).
  // The auto-whitelist step itself is untouched by this — it's what lets a
  // pending member actually join the server to receive their code.
  const memberRows = [
    ...users.map(u => ({
      ...u,
      // New registrations (since 2026-09-27) carry linkState 'pending'/'on_hold' until they link.
      linkStatus: u.linkState === 'pending' ? 'pending' : u.linkState === 'on_hold' ? 'on_hold' : (u.mcUsername ? 'linked' : 'unlinked'),
      expiresAt: u.linkDeadline || undefined,
      isPending: false
    })),
    ...pendingRegistrations
      .filter(p => !users.some(u => u.mcUsername && p.mcUsername && u.mcUsername.toLowerCase() === p.mcUsername.toLowerCase()))
      .map(p => ({
        email: p.email,
        mcUsername: p.mcUsername,
        role: 'pending',
        linkStatus: 'pending',
        isPending: true,
        expiresAt: p.expiresAt
      }))
  ];

  const filteredMembers = memberRows.filter(m => {
    const matchesSearch = (m.mcUsername || '').toLowerCase().includes(searchMember.toLowerCase()) ||
                          (m.email || '').toLowerCase().includes(searchMember.toLowerCase());
    const matchesRole = roleFilter === 'all' || m.role === roleFilter ||
      (roleFilter === 'pending' && (m.linkStatus === 'pending' || m.linkStatus === 'on_hold'));
    return matchesSearch && matchesRole;
  }).sort((a, b) => (roleRank(a.role, roles) - roleRank(b.role, roles)) ||
    (a.mcUsername || a.email || '').localeCompare(b.mcUsername || b.email || '', undefined, { sensitivity: 'base' }));

  // Resolved reports (fixed/wont_fix/duplicate) drop into the Archive view
  // 14 days after resolution — see BUG_ARCHIVE_DAYS/isBugReportArchived.
  const visibleBugReports = bugReports.filter(r => bugFilter === 'archived' ? isBugReportArchived(r) : !isBugReportArchived(r));
  const archivedBugCount = bugReports.filter(isBugReportArchived).length;

  // Roles a member can be assigned via the dropdown below — 'player' and
  // 'admin' are always offered; 'moderator' is offered even before an admin
  // has ever saved a real row for it (see Role Manager's virtual default
  // row), plus every custom role created there.
  const assignableRoles = [
    { name: 'player', label: 'Player' },
    ...(roles.some(r => r.name === 'moderator') ? [] : [{ name: 'moderator', label: 'Moderator' }]),
    ...roles.map(r => ({ name: r.name, label: r.label })),
    { name: 'admin', label: 'Admin' }
  ].sort((a, b) => compareRoles(a.name, b.name, roles, n => n));

  const isAdmin = myPermissions.isAdmin;
  const myTabs = myPermissions.tabs;
  const canAccessTab = (tabKey) => myTabs.includes(tabKey);
  // Real top-level Staff Panel tabs, i.e. everything EXCEPT the settings_*
  // sub-permissions (those only ever gate a sub-tab *inside* Settings, they
  // never become `activeTab`/`?tab=` themselves).
  const topLevelTabKeys = STAFF_TABS.filter(t => !t.parent).map(t => t.key);
  // If the active tab (possibly restored from ?tab=) isn't one this role can
  // see, fall back to the first top-level tab it actually has instead of
  // rendering a blank panel.
  // 'overview' isn't a permission — every staff role gets it, the server
  // filters its contents (see api/admin/dashboard).
  const effectiveTab = activeTab === 'overview' ? 'overview' : canAccessTab(activeTab) ? activeTab : 'overview';

  // Same idea one level down, now that Settings is 9 separately-permissioned
  // tools instead of one all-or-nothing tab (see staff-tabs.js): a role with
  // only e.g. 'settings_rules' must land on Rules Manager, not the
  // hardcoded 'general' default that role may not actually have.
  // 'roles'/'newsletter'/'files' aren't real permissions at all — always
  // admin-only, see the buttons' own comments for why that's deliberate.
  const SETTINGS_SUBTAB_ORDER = ['general', 'carousel', 'guide', 'rules', 'tips', 'vote', 'projects', 'content', 'cleanup', 'roles', 'newsletter', 'files', 'audit'];
  const canAccessSettingsSubTab = (key) => (key === 'roles' || key === 'newsletter' || key === 'files' || key === 'audit') ? isAdmin : canAccessTab(`settings_${key}`);
  const effectiveSettingsSubTab = canAccessSettingsSubTab(settingsSubTab) ? settingsSubTab : (SETTINGS_SUBTAB_ORDER.find(canAccessSettingsSubTab) || null);

  // Settings tools live in the sidebar (expanding under "Settings"), not in a
  // second bar inside the page. Selecting one also kicks off whatever lazy
  // load that tool used to trigger from its own button.
  const selectSettingsSub = (subKey) => {
    setActiveTab('settings');
    setSettingsSubTab(subKey);
    if (subKey === 'content' && !siteContentDraft) setSiteContentDraft({ ...siteContent });
    if (subKey === 'cleanup' && inactiveCandidates.length === 0 && !loadingInactive) fetchInactiveCandidates();
    if (subKey === 'newsletter' && newsletterHistory.length === 0 && !loadingNewsletterHistory) fetchNewsletterHistory();
    if (subKey === 'files' && adminFiles.length === 0 && !loadingAdminFiles) fetchAdminFiles();
  };
  // Admin-only tools (roles/newsletter/files) are deliberately not STAFF_TABS
  // entries — see canAccessSettingsSubTab and the server-side checks.
  const ADMIN_ONLY_SETTINGS = {
    roles: { label: 'Roles & Permissions', Icon: ShieldCheck, description: 'Create/edit custom staff roles and choose which Staff Panel tabs each one can access. Admin-only.' },
    newsletter: { label: 'Newsletter', Icon: Mail, description: 'Compose and send a one-off broadcast email to every opted-in player. Admin-only; a send cannot be recalled.' },
    files: { label: 'Shared Files', Icon: FolderLock, description: 'Share plugins, configs, and other files with other admins. Admin-only.' },
    audit: { label: 'Audit Log', Icon: History, description: 'Who changed what in the Staff Panel. Read-only, admin-only.' }
  };
  const settingsChildren = SETTINGS_SUBTAB_ORDER.filter(canAccessSettingsSubTab).map(subKey => {
    const t = STAFF_TABS.find(st => st.key === `settings_${subKey}`);
    const extra = ADMIN_ONLY_SETTINGS[subKey];
    return {
      key: subKey,
      label: t ? t.label : extra.label,
      description: t ? t.description : extra.description,
      Icon: t ? SETTINGS_TAB_ICONS[t.key] : extra.Icon
    };
  });

  // Sidebar structure: hubs (see NAV_HUBS), then any ungrouped tab, then Settings.
  const sidebarTab = (key) => {
    const st = STAFF_TABS.find(x => x.key === key);
    return {
      key,
      label: st.label,
      description: st.description,
      Icon: TAB_ICONS[key],
      crimson: CRIMSON_TABS.has(key),
      badge: dashboard?.stats?.find(x => x.tab === key && x.urgent)?.value || 0
    };
  };
  const placedTabKeys = new Set(NAV_HUBS.flatMap(h => h.tabs));
  const navItems = [];
  NAV_HUBS.forEach(h => {
    const kids = h.tabs.filter(k => STAFF_TABS.some(x => x.key === k) && canAccessTab(k)).map(sidebarTab);
    if (kids.length === 0) return;
    if (kids.length === 1) { navItems.push(kids[0]); return; } // a hub of one is just that tab
    navItems.push({
      key: h.key, label: h.label, description: h.description, Icon: h.Icon,
      children: kids, activeKeys: kids.map(k => k.key), activeChild: effectiveTab,
      selectKey: kids[0].key, onSelectChild: setActiveTab
    });
  });
  STAFF_TABS.filter(x => !x.parent && x.key !== 'settings' && !placedTabKeys.has(x.key) && canAccessTab(x.key))
    .forEach(x => navItems.push(sidebarTab(x.key)));
  if (canAccessTab('settings')) {
    navItems.push({ ...sidebarTab('settings'), children: settingsChildren, activeChild: effectiveSettingsSubTab, onSelectChild: selectSettingsSub });
  }
  const navGroups = [{ name: '', tabs: navItems }];

  return (
    <div className="space-y-6">
      
      {/* HEADER */}
      <div className="border-b border-sp-bronze pb-4">
        <h1 className="font-serif text-3xl font-bold text-rose-300 tracking-wide flex items-center gap-2 uppercase">
          <ShieldAlert className="w-8 h-8 text-crimson" /> {siteContent.title_staff_panel}
        </h1>
      </div>

      <div className="flex flex-col lg:flex-row gap-6 items-start">
        {/* SIDEBAR — each tab only renders if the current role has been
            granted it (see Settings > Roles & Permissions). Admin always
            sees every tab. Labels/icons come from STAFF_TABS so this can't
            drift from what Role Manager calls the same tab. */}
        <StaffSidebar
          groups={navGroups}
          active={effectiveTab}
          onSelect={setActiveTab}
          search={mainTabSearch}
          setSearch={setMainTabSearch}
        />

        <div className="flex-1 min-w-0 w-full space-y-6">

      {successMsg && (
        <div className="bg-green-500/10 border border-green-500 text-green-400 text-xs px-3 py-2 rounded-lg">
          {successMsg}
        </div>
      )}

      {error && (
        <div className="bg-crimson/20 border border-crimson text-rose-200 text-xs px-3 py-2 rounded-lg">
          {error}
        </div>
      )}

      {effectiveTab === 'overview' && (
        <StaffOverview
          dashboard={dashboard}
          loading={dashboardLoading}
          error={dashboardError}
          onNavigate={(tab) => { if (tab) setActiveTab(tab); }}
          userName={user?.mcUsername || user?.email}
        />
      )}

      {effectiveTab === 'moderation' ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          
          {/* COLUMN 1: PENALTY INITIATION PANEL */}
          <div className="lg:col-span-1 space-y-6">
            <form onSubmit={handleModerationSubmit} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass">
              <h3 className="font-serif text-base text-rose-300 uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
                <UserX className="w-5 h-5 text-crimson" /> Issue Penalty
              </h3>

              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Target In-Game Name (IGN)</label>
                <input
                  type="text"
                  required
                  value={targetUsername}
                  onChange={e => setTargetUsername(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  placeholder="e.g. NaughtySteve"
                />
              </div>

              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Sanction Action</label>
                <select
                  value={type}
                  onChange={e => setType(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                >
                  <option value="kick">KICK (Eject from current session)</option>
                  <option value="ban">BAN (Permanent quarantine)</option>
                  <option value="tempban">TEMPBAN (7-day quarantine)</option>
                  <option value="unban">UNBAN (Lift a ban)</option>
                  <option value="note">STAFF NOTE (Add to record)</option>
                </select>
              </div>

              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Violation Reason</label>
                <textarea
                  required
                  rows={4}
                  value={reason}
                  onChange={e => setReason(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed"
                  placeholder="Write specific reasons (griefing, spamming etc.)..."
                />
              </div>

              <button
                type="submit"
                className="w-full py-2.5 bg-crimson hover:bg-rose-600 border border-rose-500 rounded-lg text-xs font-bold uppercase tracking-wider text-white transition-all cursor-pointer font-serif flex items-center justify-center gap-2"
              >
                <Send className="w-4 h-4" /> Issue Broadcast Code
              </button>
            </form>
          </div>

          {/* COLUMN 2: REGISTRATION LOG & TERMINAL VIEW */}
          <div className="lg:col-span-2 space-y-6">
            
            {/* TERMINAL ACTION HISTORY */}
            <div className="bg-black border border-sp-bronze rounded-xl p-5 font-mono text-xs text-green-400 space-y-4 shadow-inner relative">
              <div className="flex items-center justify-between border-b border-sp-bronze/35 pb-2 text-text-warm/40">
                <span className="flex items-center gap-1 text-[9px] uppercase font-bold"><Terminal className="w-3.5 h-3.5" /> Broadcast Transmissions</span>
                <span className="text-[9px] bg-green-500/10 text-green-400 px-1 border border-green-500/20">LIVE</span>
              </div>

              {loading ? (
                <div className="py-12 text-center text-text-warm/30">Connecting terminal stream...</div>
              ) : logs.length === 0 ? (
                <div className="py-12 text-center text-text-warm/30">No records found. Logs are currently clear.</div>
              ) : (
                <div className="max-h-[300px] overflow-y-auto space-y-3 pr-1">
                  {logs.map((log) => (
                    <div key={log.id} className="border-l-2 border-sp-bronze/45 pl-3 py-1 text-left">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-text-warm/45">[{new Date(log.date).toLocaleTimeString()}]</span>
                        
                        <img 
                          src={`https://mc-heads.net/avatar/${log.username}`} 
                          alt="" 
                          className="w-4 h-4 rounded-sm inline-block"
                        />

                        <span className="font-bold text-white">{log.username}</span>
                        
                        <span className={`text-[9px] px-1.5 py-0.5 rounded font-bold uppercase ${
                          log.type === 'ban'
                            ? 'bg-red-500/20 border border-red-500/40 text-red-400'
                            : log.type === 'tempban'
                            ? 'bg-orange-500/20 border border-orange-500/40 text-orange-400'
                            : log.type === 'kick'
                            ? 'bg-amber-500/20 border border-amber-500/40 text-amber-400'
                            : log.type === 'unban'
                            ? 'bg-emerald-500/20 border border-emerald-500/40 text-emerald-400'
                            : 'bg-blue-500/20 border border-blue-500/40 text-blue-400'
                        }`}>
                          {log.type}
                        </span>
                        
                        <span className="text-text-warm/50 text-[10px]">by {log.staffMember}</span>
                      </div>
                      <p className="text-text-warm/85 mt-1 pl-1">Reason: &quot;{log.reason}&quot;</p>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* REGISTERED PLAYER INDEX */}
            <div className="bg-neutral-900/30 border border-sp-bronze/45 rounded-xl p-5 space-y-5">
              <React.Fragment>
                {/* Stats Summary Grid */}
                <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 bg-neutral-950/30 p-4 border border-sp-bronze/10 rounded-lg">
                  <div className="text-left">
                    <span className="block text-[8px] uppercase font-bold text-text-warm/40">Total Pilots</span>
                    <span className="font-serif text-lg font-black text-white">{users.length}</span>
                  </div>
                  <div className="text-left">
                    <span className="block text-[8px] uppercase font-bold text-text-warm/40">{siteContent.role_label_admin}s (Admin)</span>
                    <span className="font-serif text-lg font-black text-rose-300">{users.filter(u => u.role === 'admin').length}</span>
                  </div>
                  <div className="text-left">
                    <span className="block text-[8px] uppercase font-bold text-text-warm/40">{siteContent.role_label_moderator}s (Mod)</span>
                    <span className="font-serif text-lg font-black text-sp-gold">{users.filter(u => u.role === 'moderator').length}</span>
                  </div>
                  <div className="text-left">
                    <span className="block text-[8px] uppercase font-bold text-text-warm/40">Pending Links</span>
                    <span className="font-serif text-lg font-black text-orange-400">{memberRows.filter(m => m.linkStatus === 'pending' || m.linkStatus === 'on_hold').length}</span>
                  </div>
                  <div className="text-left">
                    <span className="block text-[8px] uppercase font-bold text-text-warm/40">Sanctions Issued</span>
                    <span className="font-serif text-lg font-black text-sp-copper">{logs.length}</span>
                  </div>
                </div>

                <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 border-b border-sp-bronze/25 pb-3">
                  <div className="flex flex-wrap gap-1.5">
                    {[
                      'all', 'pending',
                      // Built-ins plus any custom role created in Role Manager,
                      // in the shared role hierarchy (see roleRank in
                      // staff-tabs.js) — custom staff roles sit between
                      // moderator and player instead of trailing after player.
                      ...['admin', 'moderator', 'player', ...roles.map(r => r.name).filter(n => n !== 'moderator')]
                        .sort((a, b) => compareRoles(a, b, roles, n => roles.find(r => r.name === n)?.label || n))
                    ].map((role) => (
                      <button
                        key={role}
                        type="button"
                        onClick={() => setRoleFilter(role)}
                        className={`px-3 py-1 rounded text-[10px] font-bold uppercase transition-all cursor-pointer ${
                          roleFilter === role
                            ? role === 'pending' ? 'bg-orange-500 text-black' : 'bg-sp-gold text-black'
                            : 'bg-neutral-950/40 text-text-warm/60 border border-sp-bronze/20 hover:text-white'
                        }`}
                      >
                        {role === 'all' ? 'All Roles'
                          : role === 'pending' ? `Pending Links (${memberRows.filter(m => m.linkStatus === 'pending' || m.linkStatus === 'on_hold').length})`
                          : role === 'admin' ? `${siteContent.role_label_admin}s`
                          : role === 'moderator' ? `${siteContent.role_label_moderator}s`
                          : role === 'player' ? `${siteContent.role_label_player}s`
                          : `${roles.find(r => r.name === role)?.label || role}s`}
                      </button>
                    ))}
                  </div>

                  <input 
                    type="text"
                    placeholder="Filter players IGN/Email..."
                    value={searchMember}
                    onChange={e => setSearchMember(e.target.value)}
                    className="bg-neutral-950 border border-sp-bronze/40 text-xs text-white rounded-md px-2.5 py-1.5 focus:outline-hidden focus:border-sp-gold w-full lg:max-w-xs"
                  />
                </div>

                {loading ? (
                  <div className="py-8 text-center text-text-warm/40">Searching...</div>
                ) : filteredMembers.length === 0 ? (
                  <div className="py-8 text-center text-text-warm/40">No matching pilots found.</div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs divide-y divide-sp-bronze/10">
                      <thead>
                        <tr className="text-text-warm/50 font-bold uppercase text-[9px] tracking-wider">
                          <th className="py-2.5">Player Skin</th>
                          <th className="py-2.5">Email Address</th>
                          <th className="py-2.5">IGN</th>
                          <th className="py-2.5">Link Status</th>
                          <th className="py-2.5">Clearance Role</th>
                          <th className="py-2.5 text-right">Sanction Commands</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-sp-bronze/10">
                        {filteredMembers.map((member) => (
                          <tr key={member.isPending ? `pending-${member.email}` : member.email} className="hover:bg-neutral-950/20">
                            <td className="py-3">
                              <div className="flex items-center gap-2">
                                <PlayerAvatar
                                  mcUsername={member.mcUsername}
                                  customSkinUrl={member.customSkinUrl}
                                  size={24}
                                  className="w-6 h-6 rounded-md border border-sp-bronze/30 shadow-xs"
                                />
                              </div>
                            </td>
                            <td className="py-3 text-text-warm/85 font-mono">{member.email}</td>
                            <td className="py-3 text-white font-bold">{member.mcUsername}</td>
                            <td className="py-3">
                              <span
                                className={`inline-flex items-center gap-1.5 text-[10px] font-bold uppercase ${
                                  member.linkStatus === 'linked' ? 'text-emerald-400'
                                    : member.linkStatus === 'pending' ? 'text-orange-400'
                                    : member.linkStatus === 'on_hold' ? 'text-rose-300'
                                    : 'text-rose-400'
                                }`}
                                title={
                                  member.linkStatus === 'linked' ? 'Verified their IGN with the in-game code.'
                                    : member.linkStatus === 'pending' ? `Registered and whitelisted, but hasn't linked in-game yet${member.expiresAt ? ` — deadline ${new Date(member.expiresAt).toLocaleString()}, after that they're removed from the whitelist and kicked` : ''}.`
                                    : member.linkStatus === 'on_hold' ? "Didn't link in time: removed from the whitelist and kicked. Back on the whitelist as soon as they link on /link."
                                    : 'Never linked an in-game code — no mcUsername on this account.'
                                }
                              >
                                <span className={`w-2 h-2 rounded-full shrink-0 ${
                                  member.linkStatus === 'linked' ? 'bg-emerald-400'
                                    : member.linkStatus === 'pending' ? 'bg-orange-400'
                                    : member.linkStatus === 'on_hold' ? 'bg-rose-300'
                                    : 'bg-rose-400'
                                }`} />
                                {member.linkStatus === 'linked' ? 'Linked' : member.linkStatus === 'pending' ? 'Pending' : member.linkStatus === 'on_hold' ? 'On hold' : 'Not Linked'}
                                {member.linkStatus === 'pending' && member.expiresAt && !member.isPending && (
                                  <span className="normal-case font-normal text-text-warm/50">(until {new Date(member.expiresAt).toLocaleDateString()})</span>
                                )}
                              </span>
                            </td>
                            <td className="py-3">
                              {member.isPending ? (
                                <span className="text-[10px] uppercase font-bold text-text-warm/40 italic">Awaiting verification</span>
                              ) : isAdmin ? (
                                member.email && member.email.toLowerCase() === user.email.toLowerCase() ? (
                                  <span className="text-[10px] text-sp-gold/50 italic">Owner (Protected)</span>
                                ) : (
                                  <select
                                    value={member.role}
                                    onChange={(e) => handleRoleChange(member.email, e.target.value)}
                                    className="bg-neutral-950 border border-sp-bronze/40 text-[10px] rounded px-1.5 py-0.5 text-text-warm focus:outline-hidden focus:border-sp-gold cursor-pointer"
                                  >
                                    {assignableRoles.map(r => (
                                      <option key={r.name} value={r.name}>{r.label}</option>
                                    ))}
                                    {/* A role that was since deleted from Role Manager but is still
                                        assigned to this member — keep it selectable so the dropdown
                                        doesn't silently jump to a different role on render. */}
                                    {!assignableRoles.some(r => r.name === member.role) && (
                                      <option value={member.role}>{member.role} (deleted role)</option>
                                    )}
                                  </select>
                                )
                              ) : (
                                <span className="text-[10px] uppercase font-bold text-text-warm/40">{member.role}</span>
                              )}
                            </td>
                            <td className="py-3 text-right">
                              {member.mcUsername !== user.mcUsername ? (
                                <div className="flex items-center justify-end gap-1.5">
                                  <button
                                    onClick={() => setQuickSanctionTarget({ username: member.mcUsername, type: 'note' })}
                                    className="px-2 py-0.5 border border-sp-bronze/30 hover:border-sp-gold text-[9px] uppercase font-bold rounded-sm text-text-warm/60 hover:text-sp-gold transition-colors cursor-pointer"
                                    title="Add Note"
                                  >
                                    Note
                                  </button>
                                  <button
                                    onClick={() => setQuickSanctionTarget({ username: member.mcUsername, type: 'unban' })}
                                    className="px-2 py-0.5 border border-sp-bronze/30 hover:border-emerald-500 text-[9px] uppercase font-bold rounded-sm text-text-warm/60 hover:text-emerald-400 transition-colors cursor-pointer"
                                    title="Lift Ban"
                                  >
                                    Unban
                                  </button>
                                  {member.role !== 'admin' && member.role !== 'moderator' && (
                                    <>
                                      <button
                                        onClick={() => setQuickSanctionTarget({ username: member.mcUsername, type: 'kick' })}
                                        className="px-2 py-0.5 border border-sp-bronze/30 hover:border-sp-copper text-[9px] uppercase font-bold rounded-sm text-text-warm/60 hover:text-sp-copper transition-colors cursor-pointer"
                                        title="Eject Player"
                                      >
                                        Kick
                                      </button>
                                      <button
                                        onClick={() => setQuickSanctionTarget({ username: member.mcUsername, type: 'tempban' })}
                                        className="px-2 py-0.5 border border-sp-bronze/30 hover:border-orange-500 text-[9px] uppercase font-bold rounded-sm text-text-warm/60 hover:text-orange-400 transition-colors cursor-pointer"
                                        title="Tempban Player (7 days)"
                                      >
                                        Tempban
                                      </button>
                                      <button
                                        onClick={() => setQuickSanctionTarget({ username: member.mcUsername, type: 'ban' })}
                                        className="px-2 py-0.5 border border-sp-bronze/30 hover:border-crimson text-[9px] uppercase font-bold rounded-sm text-text-warm/60 hover:text-rose-400 transition-colors cursor-pointer"
                                        title="Ban Player"
                                      >
                                        Ban
                                      </button>
                                      {isAdmin && !member.isPending && (
                                        <button
                                          onClick={() => { setGdprError(''); setGdprDeleteConfirm(member); }}
                                          className="px-2 py-0.5 border border-crimson/40 hover:bg-crimson text-[9px] uppercase font-bold rounded-sm text-rose-300 hover:text-white transition-colors cursor-pointer"
                                          title="Permanently delete this account (GDPR erasure request)"
                                        >
                                          Delete
                                        </button>
                                      )}
                                    </>
                                  )}
                                </div>
                              ) : (
                                <span className="text-[9px] italic text-text-warm/30">Protected</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

                {/* Quick Sanction Modal */}
                {quickSanctionTarget && (
                  <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4 backdrop-blur-sm">
                    <form 
                      onSubmit={handleQuickSanctionConfirm}
                      className="w-full max-w-md bg-sp-dark border-2 border-crimson p-6 rounded-xl relative steampunk-border shadow-2xl space-y-4"
                    >
                      <button 
                        type="button"
                        onClick={() => { setQuickSanctionTarget(null); setQuickReason(''); }}
                        className="absolute top-4 right-4 text-text-warm/60 hover:text-white"
                      >
                        <X className="w-5 h-5" />
                      </button>

                      <h3 className="font-serif text-lg text-rose-300 uppercase tracking-wide border-b border-crimson/25 pb-2 flex items-center gap-2">
                        <AlertTriangle className="w-5 h-5 text-crimson" /> Issue {quickSanctionTarget.type.toUpperCase()}
                      </h3>

                      <div className="flex items-center gap-3 bg-neutral-950/40 p-2.5 rounded-lg border border-sp-bronze/10">
                        <img 
                          src={`https://mc-heads.net/avatar/${quickSanctionTarget.username}`} 
                          alt="" 
                          className="w-10 h-10 rounded-md border border-sp-bronze/20"
                        />
                        <div className="text-left">
                          <span className="block text-[8px] uppercase font-bold text-text-warm/40">Target Player</span>
                          <span className="font-bold text-white text-sm">{quickSanctionTarget.username}</span>
                        </div>
                      </div>

                      <div className="text-left">
                        <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Violation Description</label>
                        <textarea
                          required
                          rows={4}
                          value={quickReason}
                          onChange={e => setQuickReason(e.target.value)}
                          className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed"
                          placeholder="Provide the reason for this moderation broadcast code..."
                        />
                      </div>

                      <div className="flex justify-end gap-3 pt-2">
                        <button
                          type="button"
                          onClick={() => { setQuickSanctionTarget(null); setQuickReason(''); }}
                          className="px-4 py-2 border border-sp-bronze text-xs font-bold uppercase rounded-lg text-text-warm/80 hover:text-white cursor-pointer"
                        >
                          Cancel
                        </button>
                        <button
                          type="submit"
                          className="px-5 py-2 bg-crimson border border-rose-500 rounded-lg text-xs font-bold uppercase tracking-wider text-white hover:bg-rose-600 cursor-pointer font-serif"
                        >
                          Confirm Sanction
                        </button>
                      </div>
                    </form>
                  </div>
                )}

                {gdprDeleteConfirm && (
                  <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4 backdrop-blur-sm">
                    <div className="w-full max-w-md bg-sp-dark border-2 border-crimson p-6 rounded-xl relative steampunk-border shadow-2xl space-y-4 text-center">
                      <h3 className="font-serif text-lg text-rose-300 uppercase tracking-wide border-b border-crimson/25 pb-2 flex items-center justify-center gap-2">
                        <AlertTriangle className="w-5 h-5 text-crimson" /> Permanently Delete Account
                      </h3>
                      <p className="text-sm text-white">
                        Delete <span className="font-bold text-rose-300">{gdprDeleteConfirm.mcUsername || gdprDeleteConfirm.email}</span> permanently?
                      </p>
                      <p className="text-[10px] text-text-warm/50 leading-relaxed">
                        This removes their account, whitelist entry, journal, friendships, and notifications — the same erasure the Privacy Policy promises on request. It cannot be undone; they'd need to register from scratch to play again.
                      </p>
                      {gdprError && <p className="text-xs text-rose-400">{gdprError}</p>}
                      <div className="flex justify-center gap-3 pt-2">
                        <button
                          type="button"
                          onClick={() => setGdprDeleteConfirm(null)}
                          disabled={gdprDeleting}
                          className="px-4 py-2 border border-sp-bronze text-xs font-bold uppercase rounded-lg text-text-warm/80 hover:text-white cursor-pointer disabled:opacity-50"
                        >
                          Cancel
                        </button>
                        <button
                          type="button"
                          onClick={confirmGdprDelete}
                          disabled={gdprDeleting}
                          className="px-5 py-2 bg-crimson border border-rose-500 rounded-lg text-xs font-bold uppercase tracking-wider text-white hover:bg-rose-600 cursor-pointer font-serif disabled:opacity-50"
                        >
                          {gdprDeleting ? 'Deleting...' : 'Delete Permanently'}
                        </button>
                      </div>
                    </div>
                  </div>
                )}
              </React.Fragment>
            </div>

          </div>

        </div>
      ) : effectiveTab === 'polls' ? (
        <div className="space-y-6">
          <div className="border-b border-sp-bronze/25 pb-2">
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2">
              <ThumbsUp className="w-5 h-5 text-sp-copper" /> Community Polls Manager
            </h3>
            <p className="text-[10px] text-text-warm/30 italic mt-1">Polls created here appear on the public site for logged-in players to vote on — one vote per player, results shown live.</p>
          </div>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* COLUMN 1: CREATE POLL FORM */}
          <div className="lg:col-span-1 space-y-6">
            <form onSubmit={handlePollSubmit} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass">
              <h3 className="font-serif text-base text-rose-300 uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
                <Users className="w-5 h-5 text-sp-gold" /> {pollEditId ? 'Edit Community Poll' : 'Forge Community Poll'}
              </h3>

              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Question</label>
                <input
                  type="text"
                  required
                  value={pollQuestion}
                  onChange={e => setPollQuestion(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  placeholder="e.g. Which boss should spawn next?"
                />
              </div>

              <div className="space-y-3">
                <div className="flex items-center justify-between border-b border-sp-bronze/10 pb-1">
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/80">Options</label>
                  <button
                    type="button"
                    onClick={handleAddOption}
                    className="text-[9px] uppercase font-bold text-sp-gold hover:text-white transition-colors"
                  >
                    + Add Option
                  </button>
                </div>

                {pollOptions.map((opt, idx) => (
                  <div key={idx} className="flex items-center gap-2">
                    <input
                      type="text"
                      required
                      value={opt}
                      onChange={e => handleOptionChange(idx, e.target.value)}
                      className="flex-1 bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                      placeholder={`Option ${idx + 1}`}
                    />
                    {pollOptions.length > 2 && (
                      <button
                        type="button"
                        onClick={() => handleRemoveOption(idx)}
                        className="text-crimson hover:text-rose-400 text-xs font-bold font-mono px-1"
                        title="Remove option"
                      >
                        X
                      </button>
                    )}
                  </div>
                ))}
              </div>

              <div className="flex gap-2">
                {pollEditId && (
                  <button
                    type="button"
                    onClick={() => {
                      setPollEditId(null);
                      setPollQuestion('');
                      setPollOptions(['', '']);
                    }}
                    className="flex-1 py-2.5 border border-sp-bronze text-xs font-bold uppercase tracking-wider text-text-warm/60 hover:text-white rounded-lg transition-all cursor-pointer font-serif"
                  >
                    Cancel
                  </button>
                )}
                <button
                  type="submit"
                  className="flex-1 py-2.5 bg-sp-gold hover:bg-white border border-transparent rounded-lg text-xs font-bold uppercase tracking-wider text-black transition-all cursor-pointer font-serif flex items-center justify-center gap-1"
                >
                  <Plus className="w-4 h-4" /> {pollEditId ? 'Save Poll' : 'Commit Poll'}
                </button>
              </div>
            </form>
          </div>

          {/* COLUMN 2: ACTIVE POLLS LIST */}
          <div className="lg:col-span-2 space-y-4">
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2 flex items-center gap-2">
              <Users className="w-5 h-5 text-sp-copper" /> Live Polls ({polls.length})
            </h3>

            {polls.length === 0 ? (
              <div className="p-8 text-center text-text-warm/40 border border-dashed border-sp-bronze/45 rounded-xl">
                No active polls in transmission.
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {polls.map((poll) => {
                  const totalVotes = poll.options.reduce((sum, opt) => sum + (opt.votes || 0), 0);

                  return (
                    <div key={poll.id} className="bg-sp-dark/95 border border-sp-bronze rounded-xl overflow-hidden shadow-md p-4 flex flex-col justify-between gap-4">
                      <div className="space-y-2 text-left">
                        <h4 className="font-serif text-sm font-bold text-white tracking-wide uppercase">
                          {poll.question}
                        </h4>
                        
                        <div className="space-y-1.5">
                          {poll.options.map((opt, oIdx) => {
                            const votes = opt.votes || 0;
                            const pct = totalVotes > 0 ? Math.round((votes / totalVotes) * 100) : 0;
                            return (
                              <div key={oIdx} className="text-[10px] text-text-warm/75 flex justify-between border-b border-sp-bronze/10 pb-0.5">
                                <span>{opt.text}</span>
                                <span className="font-bold text-sp-gold">{votes} ({pct}%)</span>
                              </div>
                            );
                          })}
                        </div>
                      </div>

                      <div className="bg-neutral-950/45 pt-2 border-t border-sp-bronze/25 flex justify-between items-center text-[9px] text-text-warm/50">
                        <span>Total: {totalVotes} votes</span>
                        <div className="flex items-center gap-1.5">
                          <button
                            onClick={() => handlePollEdit(poll)}
                            className="p-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded transition-all cursor-pointer"
                            title="Edit Poll"
                          >
                            <Edit className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handlePollDelete(poll.id)}
                            className="px-2 py-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded font-bold uppercase transition-all cursor-pointer"
                          >
                            Delete
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
        </div>
      ) : effectiveTab === 'events' ? (
        <div className="space-y-6">
          <div className="border-b border-sp-bronze/25 pb-2">
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2">
              <Users className="w-5 h-5 text-sp-copper" /> Event Manager & RSVPs
            </h3>
            <p className="text-[10px] text-text-warm/30 italic mt-1">Events created here show up on the public /events page for players to register — max 3 events per player, enforced automatically.</p>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 text-left">
            {/* COLUMN 1: EVENT CRUD FORM */}
            <div className="lg:col-span-1 space-y-6">
              <form onSubmit={handleEventSubmit} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass">
                <h3 className="font-serif text-base text-rose-300 uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
                  <Calendar className="w-5 h-5 text-sp-gold" /> {eventEditId ? 'Edit Event' : 'Schedule Event'}
                </h3>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Event Title</label>
                  <input
                    type="text"
                    required
                    value={eventTitle}
                    onChange={e => setEventTitle(e.target.value)}
                    className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                    placeholder="e.g. Castle Siege Derby"
                  />
                </div>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Description (Markdown Supported)</label>
                  <textarea
                    required
                    rows={8}
                    value={eventDesc}
                    onChange={e => setEventDesc(e.target.value)}
                    className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed"
                    placeholder="Provide rules, reward details, coords..."
                  />
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Date</label>
                    <input
                      type="date"
                      required
                      value={eventDate}
                      onChange={e => setEventDate(e.target.value)}
                      className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                    />
                  </div>
                  <div>
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Time (Server UTC+2)</label>
                    <input
                      type="time"
                      required
                      value={eventTime}
                      onChange={e => setEventTime(e.target.value)}
                      className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Timezone</label>
                  <input
                    type="text"
                    required
                    value={eventTimezone}
                    onChange={e => setEventTimezone(e.target.value)}
                    className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  />
                </div>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Poster Image (optional)</label>
                  <input
                    type="file"
                    id="event-poster-file"
                    accept="image/*"
                    onChange={handleEventImageSelect}
                    className="w-full text-xs text-text-warm file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-sp-bronze/20 file:text-sp-gold hover:file:bg-sp-bronze/35 file:cursor-pointer cursor-pointer"
                  />

                  {(eventImagePreview || (eventExistingImage && !eventRemoveImage)) && (
                    <div className="relative mt-2 inline-block">
                      <img
                        src={eventImagePreview || eventExistingImage}
                        alt="Poster preview"
                        className="h-24 rounded-lg border border-sp-gold/30 object-cover"
                      />
                      <button
                        type="button"
                        onClick={() => {
                          setEventImageFile(null);
                          setEventImagePreview('');
                          setEventRemoveImage(true);
                          const fileInput = document.getElementById('event-poster-file');
                          if (fileInput) fileInput.value = '';
                        }}
                        className="absolute -top-2 -right-2 bg-crimson hover:bg-red-600 text-white rounded-full p-1 cursor-pointer"
                        title="Remove poster"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </div>
                  )}
                </div>

                <div className="flex gap-2">
                  {eventEditId && (
                    <button
                      type="button"
                      onClick={resetEventForm}
                      className="flex-1 py-2.5 border border-sp-bronze text-xs font-bold uppercase rounded-lg text-text-warm/80 hover:text-white transition-all cursor-pointer"
                    >
                      Cancel
                    </button>
                  )}
                  <button
                    type="submit"
                    className="flex-1 py-2.5 bg-sp-gold hover:bg-white border border-transparent rounded-lg text-xs font-bold uppercase tracking-wider text-black transition-all cursor-pointer font-serif flex items-center justify-center gap-1"
                  >
                    <Plus className="w-4 h-4" /> {eventEditId ? 'Save Event' : 'Schedule Event'}
                  </button>
                </div>
              </form>
            </div>

            {/* COLUMN 2 & 3: LIST OF SCHEDULED EVENTS WITH RSVPS */}
            <div className="lg:col-span-2 space-y-6">
              {loadingEvents ? (
                <div className="text-center py-12 text-text-warm/40 text-sm">Querying outposts...</div>
              ) : events.length === 0 ? (
                <div className="p-8 text-center text-text-warm/40 border border-dashed border-sp-bronze/45 rounded-xl bg-neutral-950/20">
                  No active events found.
                </div>
              ) : (
                <div className="space-y-6">
                  {events.map((event) => (
                    <div key={event.id} className="bg-sp-dark border border-sp-bronze rounded-xl p-5 shadow-md text-left space-y-4">
                      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-2 border-b border-sp-bronze/20 pb-2">
                        <div>
                          <div className="flex items-center gap-2">
                            <h4 className="font-serif text-base font-bold text-white uppercase tracking-wider">{event.title}</h4>
                            <div className="flex gap-1 shrink-0">
                              <button
                                onClick={() => {
                                  setEventEditId(event.id);
                                  setEventTitle(event.title);
                                  setEventDesc(event.description);
                                  setEventDate(event.date);
                                  setEventTime(event.time);
                                  setEventTimezone(event.timezone);
                                  // 'derby'/'boss' are leftover sentinel values from the old
                                  // Poster Theme dropdown, not a real uploaded image — nothing to preview.
                                  const isLegacyTheme = event.image === 'derby' || event.image === 'boss';
                                  setEventExistingImage(isLegacyTheme ? '' : (event.image || ''));
                                  setEventImageFile(null);
                                  setEventImagePreview('');
                                  setEventRemoveImage(false);
                                }}
                                className="p-1 text-sp-gold hover:text-white hover:bg-sp-bronze/20 rounded"
                                title="Edit Event"
                              >
                                <Edit className="w-3.5 h-3.5" />
                              </button>
                              <button
                                onClick={() => handleEventDelete(event.id)}
                                className="p-1 text-rose-300 hover:text-white hover:bg-crimson/20 rounded"
                                title="Delete Event"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </div>
                          <p className="text-[10px] text-text-warm/50 font-mono mt-0.5">Event ID: {event.id} &bull; Scheduled: {event.date} at {event.time} ({event.timezone})</p>
                        </div>
                        <span className="text-[10px] bg-sp-gold/15 text-sp-gold border border-sp-gold/30 px-2 py-0.5 rounded font-mono font-bold uppercase self-start">
                          {registrations.filter(r => r.eventId === event.id).length} Signed Up
                        </span>
                      </div>

                      <div className="text-xs text-text-warm/80 leading-relaxed border-b border-sp-bronze/10 pb-3">
                        {renderMarkdown(event.description)}
                      </div>

                      <div className="space-y-2">
                        <span className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-2">Participant Roster & Torch Status</span>
                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3" style={{overflow: 'visible'}}>
                          {users.map((player) => {
                            const isRegistered = registrations.some(r => r.eventId === event.id && r.username === player.mcUsername);
                            return (
                              <div key={player.email} className="bg-neutral-950/40 p-2 border border-sp-bronze/20 hover:border-sp-gold/30 rounded-lg flex items-center justify-between gap-2.5 transition-colors" style={{overflow: 'visible'}}>
                                <div className="flex items-center gap-2 truncate">
                                  <PlayerAvatar
                                    mcUsername={player.mcUsername}
                                    customSkinUrl={player.customSkinUrl}
                                    size={20}
                                    className="rounded border border-sp-bronze/35"
                                  />
                                  <span className="text-xs font-bold text-white truncate">{player.mcUsername}</span>
                                </div>
                                <label className="torch-container scale-50 shrink-0 origin-right" style={{overflow: 'visible'}}>
                                  <input
                                    type="checkbox"
                                    checked={isRegistered}
                                    onChange={() => handleStaffRegisterToggle(event.id, player.mcUsername, isRegistered)}
                                  />
                                  <div className="torch">
                                    <div className="torch-light-effect"></div>
                                    <div className="torch-glow-effect"></div>
                                    <div className="torch-particles">
                                      <span></span><span></span><span></span><span></span>
                                      <span></span><span></span><span></span><span></span>
                                    </div>
                                    <div className="torch-smoke">
                                      <span></span><span></span><span></span><span></span>
                                    </div>
                                    <div className="torch-head">
                                      <div className="torch-face top"><div></div><div></div><div></div><div></div></div>
                                      <div className="torch-face left"><div></div><div></div><div></div><div></div></div>
                                      <div className="torch-face right"><div></div><div></div><div></div><div></div></div>
                                    </div>
                                    <div className="torch-stick">
                                      <div className="torch-side torch-side-left">
                                        {Array.from({ length: 16 }).map((_, idx) => <div key={idx}></div>)}
                                      </div>
                                      <div className="torch-side torch-side-right">
                                        {Array.from({ length: 16 }).map((_, idx) => <div key={idx}></div>)}
                                      </div>
                                    </div>
                                  </div>
                                </label>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      ) : effectiveTab === 'wiki' ? (
        /* ======= WIKI LORE MANAGER TAB ======= */
        <div className="space-y-6">
          <div className="border-b border-sp-bronze/25 pb-2">
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2">
              <BookOpen className="w-5 h-5 text-sp-copper" /> Chronicles of Iekcorbar (Wiki Lore Manager)
            </h3>
            <p className="text-[10px] text-text-warm/30 italic mt-1">Edits here go live immediately on the public /wiki page — no draft/preview step, so double-check before saving.</p>
          </div>

          {/* MANAGE CATEGORIES — deleting a whole category used to mean
              editing every one of its pages by hand; this makes it a single,
              explicit, staff-only action with a clear confirmation. */}
          <div className="bg-sp-dark border border-sp-bronze p-4 rounded-xl steampunk-glass space-y-3 text-left">
            <div className="flex items-center justify-between flex-wrap gap-2">
              <h4 className="font-serif text-sm text-sp-gold uppercase tracking-wide flex items-center gap-2">
                <Tag className="w-4 h-4 text-sp-copper" /> Manage Categories
              </h4>
              <div className="flex items-center gap-3">
                {wikiNumberedCategories.length > 0 && (
                  <button
                    type="button"
                    onClick={handleStripCategoryNumbering}
                    disabled={wikiStripNumberingRunning}
                    className="text-[9px] font-bold uppercase text-sp-gold hover:text-white border border-sp-gold/40 hover:border-sp-gold rounded px-2 py-1 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                    title="Removes the leading '00:' / '01:' style numbering from these category names — a legacy stand-in for ordering, now that chapters can be drag-reordered directly."
                  >
                    {wikiStripNumberingRunning ? 'Stripping…' : `Strip Numbering (${wikiNumberedCategories.length})`}
                  </button>
                )}
                <span className="text-[9px] text-text-warm/30 italic">Deleting a category permanently removes every page inside it</span>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              {wikiCategories.map(cat => {
                const count = wikiPagesList.filter(p => p.category === cat).length;
                const isRenaming = wikiCategoryRenameEdit === cat;
                return (
                  <div key={cat} className="flex items-center gap-1.5 bg-neutral-900 border border-sp-bronze/30 rounded-lg pl-2.5 pr-1.5 py-1">
                    {isRenaming ? (
                      <>
                        <input
                          autoFocus
                          type="text"
                          value={wikiCategoryRenameValue}
                          onChange={e => setWikiCategoryRenameValue(e.target.value)}
                          onKeyDown={e => {
                            if (e.key === 'Enter') handleRenameWikiCategory(cat, wikiCategoryRenameValue);
                            if (e.key === 'Escape') setWikiCategoryRenameEdit(null);
                          }}
                          className="bg-neutral-950 border border-sp-gold text-white text-xs rounded px-1.5 py-0.5 w-36 focus:outline-hidden"
                        />
                        <button
                          type="button"
                          onClick={() => handleRenameWikiCategory(cat, wikiCategoryRenameValue)}
                          disabled={wikiCategoryRenaming}
                          className="p-0.5 text-emerald-400 hover:text-emerald-300 cursor-pointer disabled:opacity-40"
                          title="Save"
                        >
                          <Check className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setWikiCategoryRenameEdit(null)}
                          className="p-0.5 text-text-warm/40 hover:text-white cursor-pointer"
                          title="Cancel"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </>
                    ) : (
                      <>
                        <span className="text-xs text-text-warm/80">{cat}</span>
                        <span className="text-[9px] text-sp-copper bg-neutral-950 px-1.5 py-0.5 rounded">{count}</span>
                        <button
                          type="button"
                          onClick={() => { setWikiCategoryRenameEdit(cat); setWikiCategoryRenameValue(cat); }}
                          className="p-0.5 text-text-warm/30 hover:text-sp-gold cursor-pointer"
                          title={`Rename category "${cat}"`}
                        >
                          <Edit className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setWikiCategoryDeleteConfirm(cat)}
                          disabled={count === 0}
                          className="p-0.5 text-text-warm/30 hover:text-crimson disabled:opacity-20 disabled:cursor-not-allowed cursor-pointer"
                          title={count === 0 ? 'No pages to delete' : `Delete category "${cat}"`}
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 text-left">
            {/* COLUMN 1: WIKI LORE EDIT FORM */}
            <div className="lg:col-span-1 space-y-6">
              <form onSubmit={handleWikiSubmit} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass">
                <h3 className="font-serif text-base text-rose-300 uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
                  <BookOpen className="w-5 h-5 text-sp-gold" /> {wikiEditId ? 'Edit Chronicle' : 'Transcribe Chronicle'}
                </h3>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Article Title</label>
                  <input
                    type="text"
                    required
                    value={wikiTitle}
                    onChange={e => setWikiTitle(e.target.value)}
                    className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                    placeholder="e.g. Races: Elves"
                  />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <div>
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Category</label>
                    <select
                      value={isWikiCustomCategory ? 'NEW_CUSTOM' : wikiCategory}
                      onChange={e => {
                        const val = e.target.value;
                        if (val === 'NEW_CUSTOM') {
                          setIsWikiCustomCategory(true);
                        } else {
                          setIsWikiCustomCategory(false);
                          setWikiCategory(val);
                        }
                      }}
                      className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-2 py-2 focus:outline-hidden focus:border-sp-gold cursor-pointer"
                    >
                      {!wikiCategory && <option value="" disabled>-- Select a category --</option>}
                      {wikiCategories.map(cat => (
                        <option key={cat} value={cat}>{cat}</option>
                      ))}
                      <option value="NEW_CUSTOM">+ Add Custom category</option>
                    </select>
                  </div>

                  {isWikiCustomCategory && (
                    <div>
                      <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Custom Category</label>
                      <input
                        type="text"
                        required
                        value={wikiCustomCategory}
                        onChange={e => setWikiCustomCategory(e.target.value)}
                        className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                        placeholder="Custom name..."
                      />
                    </div>
                  )}
                </div>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Parent Lore Page (Hierarchy)</label>
                  <select
                    value={wikiParentId}
                    onChange={e => setWikiParentId(e.target.value)}
                    className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-2 py-2 focus:outline-hidden focus:border-sp-gold cursor-pointer"
                  >
                    <option value="">-- No Parent (Root Page) --</option>
                    {wikiPages.filter(p => p.id !== wikiEditId).map(p => (
                      <option key={p.id} value={p.id}>{p.title}</option>
                    ))}
                  </select>
                </div>

                {/* TEXTAREA & PREVIEW */}
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/80">Content (Markdown Supported)</label>
                    <button
                      type="button"
                      onClick={() => setWikiLegendOpen(o => !o)}
                      className="flex items-center gap-1 text-[9px] uppercase font-bold text-sp-copper hover:text-sp-gold cursor-pointer"
                    >
                      <HelpCircle className="w-3 h-3" /> Markup Legend {wikiLegendOpen ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                    </button>
                  </div>

                  {/* Cheat-sheet for the hand-rolled renderer in src/lib/markdown.js —
                      there is no rich-text toolbar here (unlike RichTextEditor
                      elsewhere), so this is the only place staff can see what
                      syntax exists and what color/size it actually renders as. */}
                  {wikiLegendOpen && (
                    <div className="mb-2 border border-sp-bronze/30 rounded-lg bg-neutral-950/40 p-3 text-[10px] text-text-warm/70 space-y-1.5 font-mono">
                      <p><code className="text-sp-gold">**bold text**</code> → <strong className="text-sp-gold font-sans not-italic">bold, gold</strong></p>
                      <p><code className="text-sp-gold">## Big Header</code> → large gold heading with an underline</p>
                      <p><code className="text-sp-gold">### Medium Header</code> → medium gold heading</p>
                      <p><code className="text-sp-gold">#### Small Header</code> → small <span className="text-sp-copper font-sans not-italic font-bold">copper/orange</span> heading</p>
                      <p><code className="text-sp-gold">[link text](https://example.com)</code> → gold, clickable link</p>
                      <p><code className="text-sp-gold">* item</code> or <code className="text-sp-gold">- item</code> (one per line) → bullet list</p>
                      <p><code className="text-sp-gold">&gt; a quote</code> → indented gold-bordered quote box</p>
                      <p><code className="text-sp-gold">![alt text](image-url)</code> → inline image</p>
                      <p><code className="text-sp-gold">---</code> (on its own line) → horizontal divider</p>
                      <p><code className="text-sp-gold">| Col A | Col B |</code> / <code className="text-sp-gold">|---|---|</code> / <code className="text-sp-gold">| val | val |</code> → table (dashes row is optional, marks the first row as a header)</p>
                      <p className="text-text-warm/40 italic pt-1 border-t border-sp-bronze/20">There's no way to pick an arbitrary custom color — these are the only styled elements the renderer supports. Regular text stays plain.</p>
                    </div>
                  )}

                  <textarea
                    required
                    rows={10}
                    value={wikiContent}
                    onChange={e => setWikiContent(e.target.value)}
                    className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold font-mono leading-relaxed"
                    placeholder="Write details of races, brewery formulas, or server laws..."
                  />
                </div>

                {/* FILE ATTACHMENTS (PDFs/screenshots) — only once the page
                    actually exists, since a file needs a real id to attach
                    to. Separate from the image-in-markdown uploader above. */}
                {wikiEditId ? (
                  <div className="space-y-2 border-t border-sp-bronze/20 pt-3">
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/80">Attached Files (Images, GIFs, or PDF)</label>
                    <p className="text-[9px] text-text-warm/40">Optional. The first image or GIF also becomes this page's cover thumbnail in the public wiki index — works the same for a subtopic as for a top-level chapter. Everything attached still appears as a download/preview list under the article too.</p>
                    {(wikiPages.find(p => p.id === wikiEditId)?.attachments || []).map(att => (
                      <div key={att.url} className="flex items-center justify-between gap-2 bg-neutral-900 border border-sp-bronze/30 rounded-lg px-2.5 py-1.5">
                        <a href={att.url} target="_blank" rel="noopener noreferrer" className="text-[10px] text-sp-gold hover:text-white truncate flex items-center gap-1.5 min-w-0">
                          <Paperclip className="w-3 h-3 shrink-0" /> <span className="truncate">{att.name}</span>
                        </a>
                        <button type="button" onClick={() => handleWikiAttachmentRemove(att.url)} className="p-0.5 text-text-warm/40 hover:text-crimson cursor-pointer shrink-0" title="Remove">
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))}
                    <label className="flex items-center justify-center gap-1.5 py-2 border border-dashed border-sp-bronze/40 rounded-lg text-[10px] uppercase font-bold text-text-warm/50 hover:text-sp-gold hover:border-sp-gold cursor-pointer transition-all">
                      <Paperclip className="w-3.5 h-3.5" /> {wikiAttachmentUploading ? 'Uploading…' : 'Attach File'}
                      <input type="file" accept="image/*,application/pdf" className="hidden" disabled={wikiAttachmentUploading} onChange={handleWikiAttachmentUpload} />
                    </label>
                    {wikiAttachmentError && <p className="text-[10px] text-rose-400">{wikiAttachmentError}</p>}
                  </div>
                ) : (
                  <p className="text-[9px] text-text-warm/30 italic border-t border-sp-bronze/20 pt-3">Save this page first to attach PDF or image files to it.</p>
                )}

                <div className="flex gap-2">
                  {wikiEditId && (
                    <button
                      type="button"
                      onClick={() => {
                        setWikiEditId(null);
                        setWikiTitle('');
                        setWikiContent('');
                        setWikiParentId('');
                        setIsWikiCustomCategory(false);
                        setWikiCustomCategory('');
                        setWikiUploadedImages([]);
                        setWikiImageError('');
                        setWikiAttachmentError('');
                      }}
                      className="flex-1 py-2.5 border border-sp-bronze text-xs font-bold uppercase rounded-lg text-text-warm/80 hover:text-white transition-all cursor-pointer"
                    >
                      Cancel
                    </button>
                  )}
                  <button
                    type="submit"
                    className="flex-1 py-2.5 bg-sp-gold hover:bg-white border border-transparent rounded-lg text-xs font-bold uppercase tracking-wider text-black transition-all cursor-pointer font-serif flex items-center justify-center gap-1"
                  >
                    <Plus className="w-4 h-4" /> {wikiEditId ? 'Save Article' : 'Commit Lore'}
                  </button>
                </div>
              </form>
            </div>

            {/* COLUMN 2 & 3: WIKI DIRECTORY TREE & DETAILS VIEW */}
            <div className="lg:col-span-2 space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-5 gap-6 items-start">

                {/* Tree listing */}
                <div className="md:col-span-2 border border-sp-bronze/30 rounded-xl bg-neutral-950/20 overflow-hidden">
                  <span className="block px-3 py-2 text-[10px] uppercase font-bold text-sp-gold bg-neutral-900/60 tracking-wider">Archives Directory</span>

                  {/* Search + chapter chips — this is the actual fix for "hunting
                      for the right article": search matches sitewide, chips jump
                      straight to one chapter, instead of scrolling one long
                      undifferentiated list of every page in the wiki. */}
                  <div className="p-2.5 space-y-2 border-b border-sp-bronze/10 bg-neutral-950/30">
                    <div className="relative">
                      <Search className="w-3.5 h-3.5 text-text-warm/40 absolute left-2.5 top-2" />
                      <input
                        type="text"
                        value={wikiTreeSearch}
                        onChange={e => setWikiTreeSearch(e.target.value)}
                        placeholder="Search title, content, category..."
                        className="w-full bg-neutral-950 border border-sp-bronze/40 text-white text-xs rounded-lg pl-8 pr-7 py-1.5 focus:outline-hidden focus:border-sp-gold"
                      />
                      {wikiTreeSearch && (
                        <button
                          type="button"
                          onClick={() => setWikiTreeSearch('')}
                          className="absolute right-2 top-1.5 text-text-warm/40 hover:text-white cursor-pointer"
                          title="Clear search"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>
                    {wikiCategories.length > 0 && (
                      <div className="flex flex-wrap gap-1">
                        <button
                          type="button"
                          onClick={() => setWikiTreeCategoryFilter('')}
                          className={`px-2 py-0.5 text-[9px] font-bold uppercase rounded border transition-all cursor-pointer ${
                            !wikiTreeCategoryFilter
                              ? 'bg-sp-gold/15 border-sp-gold text-sp-gold'
                              : 'border-sp-bronze/30 text-text-warm/60 hover:text-white hover:border-sp-bronze'
                          }`}
                        >
                          All
                        </button>
                        {wikiCategories.map(cat => (
                          <button
                            key={cat}
                            type="button"
                            onClick={() => setWikiTreeCategoryFilter(prev => prev === cat ? '' : cat)}
                            className={`px-2 py-0.5 text-[9px] font-bold uppercase rounded border transition-all cursor-pointer ${
                              wikiTreeCategoryFilter === cat
                                ? 'bg-sp-gold/15 border-sp-gold text-sp-gold'
                                : 'border-sp-bronze/30 text-text-warm/60 hover:text-white hover:border-sp-bronze'
                            }`}
                          >
                            {cat}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  {loadingWiki ? (
                    <div className="p-4 text-xs italic text-text-warm/40">Querying outposts...</div>
                  ) : wikiPages.length === 0 ? (
                    <div className="p-4 text-xs italic text-text-warm/40">No records found.</div>
                  ) : wikiTreeFiltered.length === 0 ? (
                    <div className="p-4 text-xs italic text-text-warm/40 text-center space-y-1.5">
                      <span className="block">No chronicles match{wikiTreeIsSearching ? ` "${wikiTreeSearch}"` : ''}{wikiTreeCategoryFilter ? ` in "${wikiTreeCategoryFilter}"` : ''}.</span>
                      <button
                        type="button"
                        onClick={() => { setWikiTreeSearch(''); setWikiTreeCategoryFilter(''); }}
                        className="text-[9px] font-bold uppercase text-sp-gold hover:text-white cursor-pointer"
                      >
                        Clear filters
                      </button>
                    </div>
                  ) : wikiTreeIsFlat ? (
                    /* FLAT RESULTS — search and/or a chapter chip is active, so
                       nesting stops helping; just list every match with its
                       chapter (and parent, if it's a subtopic) called out. */
                    <div className="divide-y divide-sp-bronze/5 max-h-[440px] overflow-y-auto">
                      {wikiTreeFiltered.map(p => {
                        const parent = p.parentId ? wikiPagesList.find(par => par.id === p.parentId) : null;
                        return (
                          <button
                            key={p.id}
                            onClick={() => {
                              setSelectedWikiPage(p);
                              setWikiActiveViewTab('article');
                              setWikiPreviewRevisionContent(null);
                            }}
                            className={`w-full text-left text-xs px-3 py-2 transition-all flex items-center justify-between gap-2 ${
                              selectedWikiPage?.id === p.id ? 'bg-sp-gold/15 text-sp-gold font-bold' : 'text-text-warm hover:bg-neutral-900/50'
                            }`}
                          >
                            <span className="min-w-0">
                              <span className="block truncate">{p.title}</span>
                              {parent && (
                                <span className="block truncate text-[9px] text-text-warm/40 font-normal">&bull; under {parent.title}</span>
                              )}
                            </span>
                            <span className="text-[8px] bg-neutral-900 px-1 py-0.2 rounded text-sp-copper shrink-0">{p.category}</span>
                          </button>
                        );
                      })}
                    </div>
                  ) : (
                    /* GROUPED (BROWSING) VIEW — chapters as collapsible headers,
                       same top-level/subtopic nesting as the public wiki, so
                       staff aren't scrolling past every other chapter to find
                       the one they actually want. */
                    <div className="divide-y divide-sp-bronze/10 max-h-[440px] overflow-y-auto">
                      {wikiTreeGroups.map(({ category, topPages }) => {
                        const isCollapsed = !!wikiCollapsedCategories[category];
                        return (
                          <div key={category}>
                            <button
                              type="button"
                              onClick={() => setWikiCollapsedCategories(prev => ({ ...prev, [category]: !prev[category] }))}
                              className="w-full flex items-center gap-1.5 px-3 py-1.5 text-[10px] font-bold uppercase text-text-warm/70 hover:text-sp-gold bg-neutral-900/40 cursor-pointer"
                            >
                              {isCollapsed ? <ChevronRight className="w-3 h-3 shrink-0" /> : <ChevronDown className="w-3 h-3 shrink-0" />}
                              {isCollapsed ? <Folder className="w-3.5 h-3.5 shrink-0" /> : <FolderOpen className="w-3.5 h-3.5 shrink-0" />}
                              <span className="truncate">{category}</span>
                              <span className="ml-auto text-text-warm/40 normal-case font-normal">{topPages.length}</span>
                            </button>
                            {!isCollapsed && (
                              <div className="divide-y divide-sp-bronze/5">
                                {topPages.map(p => (
                                  <div key={p.id} className="p-2 space-y-1">
                                    <button
                                      onClick={() => {
                                        setSelectedWikiPage(p);
                                        setWikiActiveViewTab('article');
                                        setWikiPreviewRevisionContent(null);
                                      }}
                                      className={`w-full text-left text-xs px-2 py-1.5 rounded transition-all flex items-center justify-between ${
                                        selectedWikiPage?.id === p.id ? 'bg-sp-gold/15 text-sp-gold font-bold' : 'text-text-warm hover:bg-neutral-900/50'
                                      }`}
                                    >
                                      <span className="truncate">{p.title}</span>
                                    </button>

                                    {/* Children rendering */}
                                    {wikiPagesList.filter(c => c.parentId === p.id).map(c => (
                                      <button
                                        key={c.id}
                                        onClick={() => {
                                          setSelectedWikiPage(c);
                                          setWikiActiveViewTab('article');
                                          setWikiPreviewRevisionContent(null);
                                        }}
                                        className={`w-full text-left text-xs pl-6 pr-2 py-1 rounded transition-all flex items-center justify-between ${
                                          selectedWikiPage?.id === c.id ? 'bg-sp-gold/15 text-sp-gold font-bold' : 'text-text-warm/75 hover:bg-neutral-900/50'
                                        }`}
                                      >
                                        <span className="truncate">&bull; {c.title}</span>
                                        <span className="text-[8px] bg-neutral-900 px-1 py-0.2 rounded text-sp-copper">{c.category}</span>
                                      </button>
                                    ))}
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                {/* Details view panel */}
                <div className="md:col-span-3 space-y-4">
                  {selectedWikiPage ? (
                    <div className="bg-sp-dark border border-sp-bronze rounded-xl p-5 shadow-md space-y-4">
                      <div className="flex justify-between items-start border-b border-sp-bronze/20 pb-2">
                        <div>
                          <span className="text-[8px] bg-sp-gold/15 border border-sp-gold/30 text-sp-gold px-1.5 py-0.2 rounded uppercase">{selectedWikiPage.category}</span>
                          <h4 className="font-serif text-base font-bold text-white uppercase tracking-wider mt-1">{selectedWikiPage.title}</h4>
                        </div>
                        <div className="flex gap-1.5 shrink-0">
                          <button
                            onClick={() => {
                              setWikiEditId(selectedWikiPage.id);
                              setWikiTitle(selectedWikiPage.title);
                              setWikiCategory(selectedWikiPage.category);
                              setWikiContent(selectedWikiPage.content);
                              setWikiParentId(selectedWikiPage.parentId || '');
                              if (!wikiCategories.includes(selectedWikiPage.category)) {
                                setIsWikiCustomCategory(true);
                                setWikiCustomCategory(selectedWikiPage.category);
                              } else {
                                setIsWikiCustomCategory(false);
                              }
                            }}
                            className="p-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded"
                            title="Edit Lore"
                          >
                            <Edit className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleWikiDelete(selectedWikiPage)}
                            className="p-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded"
                            title="Delete Lore"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>

                      {/* Article vs Revisions Tab */}
                      <div className="flex bg-neutral-950/45 p-1 rounded-lg border border-sp-bronze/10 max-w-[200px]">
                        <button
                          onClick={() => { setWikiActiveViewTab('article'); setWikiPreviewRevisionContent(null); }}
                          className={`flex-1 px-3 py-1 text-[9px] uppercase font-bold rounded transition-all ${
                            wikiActiveViewTab === 'article' && !wikiPreviewRevisionContent ? 'bg-sp-gold text-black' : 'text-text-warm/60 hover:text-white'
                          }`}
                        >
                          Lore
                        </button>
                        <button
                          onClick={() => setWikiActiveViewTab('revisions')}
                          className={`flex-1 px-3 py-1 text-[9px] uppercase font-bold rounded transition-all ${
                            wikiActiveViewTab === 'revisions' ? 'bg-sp-gold text-black' : 'text-text-warm/60 hover:text-white'
                          }`}
                        >
                          Revisions ({selectedWikiPage.revisions?.length || 0})
                        </button>
                      </div>

                      {/* Main display */}
                      {wikiActiveViewTab === 'revisions' ? (
                        <div className="space-y-4">
                          <span className="block text-[9px] uppercase font-bold text-sp-gold border-b border-sp-bronze/10 pb-1">Specimen Revisions Logs</span>
                          
                          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-start">
                            <div className="divide-y divide-sp-bronze/15 border border-sp-bronze/20 rounded bg-neutral-950/20 max-h-[200px] overflow-y-auto">
                              <div className="p-2 text-[10px] bg-sp-gold/5 flex justify-between items-center">
                                <div>
                                  <span className="block font-bold text-sp-gold">v{(selectedWikiPage.revisions?.length || 0) + 1} (Active)</span>
                                  <span className="block text-[8px] text-text-warm/40">By {selectedWikiPage.updatedBy} &bull; {new Date(selectedWikiPage.updatedAt).toLocaleDateString()}</span>
                                </div>
                              </div>
                              {selectedWikiPage.revisions?.map((rev, idx) => (
                                <div key={idx} className="p-2 text-[10px] flex justify-between items-center hover:bg-neutral-900/20">
                                  <div>
                                    <span className="block font-bold text-white">v{selectedWikiPage.revisions.length - idx}</span>
                                    <span className="block text-[8px] text-text-warm/40">By {rev.updatedBy} &bull; {new Date(rev.updatedAt).toLocaleDateString()}</span>
                                  </div>
                                  <div className="flex gap-1.5 shrink-0">
                                    <button
                                      type="button"
                                      onClick={() => setWikiPreviewRevisionContent(rev.content)}
                                      className="px-1.5 py-0.5 bg-neutral-900 border border-sp-bronze/35 text-[8px] uppercase font-bold text-sp-gold rounded"
                                    >
                                      Preview
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => handleWikiRestoreRevision(selectedWikiPage.id, idx)}
                                      className="px-1.5 py-0.5 bg-sp-gold text-black text-[8px] uppercase font-bold rounded"
                                    >
                                      Rollback
                                    </button>
                                  </div>
                                </div>
                              ))}
                            </div>

                            <div className="border border-sp-bronze/20 p-3 rounded bg-neutral-950/30 text-xs min-h-[120px] max-h-[200px] overflow-y-auto space-y-2">
                              {wikiPreviewRevisionContent ? (
                                <>
                                  <span className="block text-[8px] uppercase font-bold text-rose-300">Archival Preview:</span>
                                  <div className="space-y-2 text-[11px] text-text-warm/85">{renderMarkdown(wikiPreviewRevisionContent)}</div>
                                </>
                              ) : (
                                <span className="block text-[10px] text-text-warm/30 italic text-center py-8">Select revision preview</span>
                              )}
                            </div>
                          </div>
                        </div>
                      ) : (
                        <div className="space-y-3 leading-relaxed text-xs text-text-warm/95 text-left border border-sp-bronze/10 p-4 rounded-lg bg-neutral-950/20">
                          {renderMarkdown(wikiPreviewRevisionContent || selectedWikiPage.content)}
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="border border-dashed border-sp-bronze/45 rounded-xl p-12 text-center text-text-warm/30 text-xs italic bg-neutral-950/10">
                      Select an article from the directory on the left to display its details.
                    </div>
                  )}
                </div>

              </div>
            </div>

          </div>

          {/* DELETE PAGE CONFIRM MODAL */}
          {wikiDeleteConfirm && (
            <div className="fixed inset-0 bg-black/70 backdrop-blur-xs flex items-center justify-center p-4 z-50">
              <div className="bg-sp-dark border-2 border-crimson p-6 rounded-xl w-full max-w-md shadow-2xl relative steampunk-glass space-y-4">
                <h3 className="font-serif text-lg text-rose-300 uppercase tracking-wide border-b border-crimson/25 pb-2 flex items-center gap-2">
                  <AlertTriangle className="w-5 h-5" /> Delete Chronicle
                </h3>
                <div className="text-xs space-y-2 text-left">
                  <p className="text-text-warm/70">
                    Delete <span className="text-white font-bold">"{wikiDeleteConfirm.title}"</span> ({wikiDeleteConfirm.category}) permanently, including its revision history?
                  </p>
                  {wikiPagesList.filter(c => c.parentId === wikiDeleteConfirm.id).length > 0 && (
                    <p className="text-[10px] text-amber-400 italic flex items-start gap-1.5 bg-amber-500/10 border border-amber-500/25 rounded-lg px-2.5 py-2">
                      <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                      This chronicle has {wikiPagesList.filter(c => c.parentId === wikiDeleteConfirm.id).length} subtopic(s). They will NOT be deleted — they become top-level chapters instead.
                    </p>
                  )}
                </div>
                <div className="flex gap-3 pt-1">
                  <button
                    type="button"
                    onClick={() => setWikiDeleteConfirm(null)}
                    className="flex-1 py-2 border border-sp-bronze text-xs uppercase rounded-lg text-text-warm/60 hover:text-white transition-all cursor-pointer font-serif"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={confirmDeleteWikiPage}
                    className="flex-1 py-2 bg-crimson text-white font-bold uppercase text-xs rounded-lg hover:bg-rose-500 transition-all cursor-pointer font-serif flex items-center justify-center gap-2"
                  >
                    <Trash2 className="w-3.5 h-3.5" /> Delete Permanently
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* DELETE CATEGORY CONFIRM MODAL */}
          {wikiCategoryDeleteConfirm && (
            <div className="fixed inset-0 bg-black/70 backdrop-blur-xs flex items-center justify-center p-4 z-50">
              <div className="bg-sp-dark border-2 border-crimson p-6 rounded-xl w-full max-w-md shadow-2xl relative steampunk-glass space-y-4">
                <h3 className="font-serif text-lg text-rose-300 uppercase tracking-wide border-b border-crimson/25 pb-2 flex items-center gap-2">
                  <AlertTriangle className="w-5 h-5" /> Delete Category
                </h3>
                <div className="text-xs space-y-2 text-left">
                  <p className="text-text-warm/70">
                    Permanently delete the category <span className="text-white font-bold">"{wikiCategoryDeleteConfirm}"</span> and every page inside it:
                  </p>
                  <ul className="max-h-32 overflow-y-auto border border-crimson/20 rounded-lg divide-y divide-crimson/10 bg-crimson/5">
                    {wikiPagesList.filter(p => p.category === wikiCategoryDeleteConfirm).map(p => (
                      <li key={p.id} className="px-2.5 py-1.5 text-[10px] text-rose-200 truncate">{p.title}</li>
                    ))}
                  </ul>
                  <p className="text-[10px] text-amber-400 italic">This cannot be undone.</p>
                </div>
                <div className="flex gap-3 pt-1">
                  <button
                    type="button"
                    onClick={() => setWikiCategoryDeleteConfirm(null)}
                    disabled={wikiCategoryDeleting}
                    className="flex-1 py-2 border border-sp-bronze text-xs uppercase rounded-lg text-text-warm/60 hover:text-white transition-all cursor-pointer font-serif disabled:opacity-40"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleDeleteWikiCategory}
                    disabled={wikiCategoryDeleting}
                    className="flex-1 py-2 bg-crimson text-white font-bold uppercase text-xs rounded-lg hover:bg-rose-500 transition-all cursor-pointer font-serif flex items-center justify-center gap-2 disabled:opacity-60"
                  >
                    {wikiCategoryDeleting ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Trash2 className="w-3.5 h-3.5" />}
                    {wikiCategoryDeleting ? 'Deleting…' : 'Delete Category'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      ) : effectiveTab === 'updates' ? (
        <div className="space-y-6">
          <div className="border-b border-sp-bronze/25 pb-2">
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-sp-copper" /> Update Log Manager
            </h3>
            <p className="text-[10px] text-text-warm/30 italic mt-1">Posts patch notes/announcements shown on the public /updates page — this is separate from marking a Bug Report as Fixed, which does NOT post here automatically.</p>
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 text-left">
          {/* COLUMN 1: FORGE UPDATE FORM & CATEGORY MANAGER */}
          <div className="lg:col-span-1 space-y-6">
            <form onSubmit={handleUpdateSubmit} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass">
              <h3 className="font-serif text-base text-rose-300 uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
                <Sparkles className="w-5 h-5 text-sp-gold" /> {updateId ? 'Edit Update Log' : 'Forge Update Log'}
              </h3>

              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Update Title</label>
                <input
                  type="text"
                  required
                  value={updateTitle}
                  onChange={e => setUpdateTitle(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  placeholder="e.g. Sakura Season Release"
                />
              </div>

              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Category / Tag</label>
                <select
                  value={updateCategory}
                  onChange={e => setUpdateCategory(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold cursor-pointer"
                >
                  {updateCategories.map(cat => (
                    <option key={cat} value={cat}>{cat}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Changelog Content (Markdown Supported)</label>
                <textarea
                  required
                  rows={8}
                  value={updateContent}
                  onChange={e => setUpdateContent(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed"
                  placeholder="*   **Feature**: Added pressurized boilers...\n*   **Fix**: Fixed Kitsune jump bug..."
                />
              </div>

              <div className="flex gap-2">
                {updateId && (
                  <button
                    type="button"
                    onClick={() => {
                      setUpdateId('');
                      setUpdateTitle('');
                      if (updateCategories.length > 0) setUpdateCategory(updateCategories[0]);
                      setUpdateContent('');
                    }}
                    className="flex-1 py-2.5 border border-sp-bronze text-xs font-bold uppercase rounded-lg text-text-warm/80 hover:text-white transition-all cursor-pointer"
                  >
                    Cancel
                  </button>
                )}
                <button
                  type="submit"
                  className="flex-1 py-2.5 bg-sp-gold hover:bg-white border border-transparent rounded-lg text-xs font-bold uppercase tracking-wider text-black transition-all cursor-pointer font-serif flex items-center justify-center gap-1"
                >
                  <Plus className="w-4 h-4" /> {updateId ? 'Save Log' : 'Commit Log'}
                </button>
              </div>
            </form>

            {/* CATEGORIES CRUD MANAGER PANEL */}
            <form onSubmit={handleCategoryCreateSubmit} className="bg-sp-dark border border-sp-bronze p-4 rounded-xl space-y-3 steampunk-glass">
              <h4 className="font-serif text-xs text-sp-gold uppercase tracking-wide border-b border-sp-bronze/10 pb-1.5 flex items-center gap-1.5">
                <Tag className="w-4 h-4 text-sp-copper" /> Forge Update Category
              </h4>
              <div className="flex gap-2">
                <input
                  type="text"
                  required
                  value={newCategoryName}
                  onChange={e => setNewCategoryName(e.target.value)}
                  placeholder="New category name..."
                  className="flex-1 bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-2.5 py-1.5 focus:outline-hidden focus:border-sp-gold"
                />
                <button
                  type="submit"
                  className="px-3 py-1.5 bg-sp-gold hover:bg-white text-black font-bold uppercase rounded-lg text-[10px] transition-all cursor-pointer font-serif shrink-0"
                >
                  Create
                </button>
              </div>
              <div className="flex flex-wrap gap-1.5 pt-1.5">
                {updateCategories.map(cat => (
                  <div key={cat} className="flex items-center gap-1.5 bg-neutral-950/40 border border-sp-bronze/20 px-2.5 py-0.5 rounded-md text-[9px] font-mono text-text-warm/80">
                    <span>{cat}</span>
                    {cat !== 'General' && (
                      <button
                        type="button"
                        onClick={() => handleCategoryDelete(cat)}
                        className="text-rose-400 hover:text-red-500 font-bold font-mono px-0.5"
                        title={`Delete category ${cat}`}
                      >
                        ×
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </form>
          </div>

          {/* COLUMN 2: LIST OF COMMITTED UPDATES */}
          <div className="lg:col-span-2 space-y-4">
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2 flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-sp-copper" /> Committed Updates ({updates.length})
            </h3>

            {loadingUpdates ? (
              <div className="py-8 text-center text-xs text-text-warm/40 italic">Retrieving update logs...</div>
            ) : updates.length === 0 ? (
              <div className="p-8 text-center text-text-warm/40 border border-dashed border-sp-bronze/45 rounded-xl">
                No update logs committed yet.
              </div>
            ) : (
              <div className="space-y-4">
                {updates.map((upd) => (
                  <div key={upd.id} className="bg-sp-dark/95 border border-sp-bronze rounded-xl p-4 flex flex-col justify-between gap-4">
                    <div className="flex justify-between items-start gap-4 text-left">
                      <div className="text-left space-y-1 min-w-0">
                        <h4 className="font-serif text-sm font-bold text-white uppercase tracking-wide truncate">
                          {upd.title}
                        </h4>
                        <div className="flex gap-2 items-center text-[9px] text-text-warm/50 font-mono">
                          <span className="bg-sp-gold/10 border border-sp-gold/30 text-sp-gold px-1.5 py-0.2 rounded uppercase">{upd.category}</span>
                          <span>By {upd.author}</span>
                          <span>{new Date(upd.date).toLocaleDateString()}</span>
                        </div>
                      </div>
                      
                      <div className="flex gap-1.5 shrink-0">
                        <button
                          onClick={() => handleUpdateEdit(upd)}
                          className="p-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded transition-all cursor-pointer"
                          title="Edit Log"
                        >
                          <Edit className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => handleUpdateDelete(upd.id)}
                          className="p-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded transition-all cursor-pointer"
                          title="Delete Log"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
        </div>
      ) : effectiveTab === 'news' ? (
        <div className="space-y-6">
          <div className="border-b border-sp-bronze/25 pb-2">
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2">
              <Newspaper className="w-5 h-5 text-sp-copper" /> News &amp; Upcoming Manager
            </h3>
            <p className="text-[10px] text-text-warm/30 italic mt-1">Posts Minecraft news and planned server/website features shown on the public /news page. Publishing (or the topic below) decides whose bell gets pinged — only players following that topic.</p>
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 text-left">
          {/* COLUMN 1: NEWS POST FORM & TOPIC MANAGER */}
          <div className="lg:col-span-1 space-y-6">
            <form onSubmit={handleNewsSubmit} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass">
              <h3 className="font-serif text-base text-rose-300 uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
                <Newspaper className="w-5 h-5 text-sp-gold" /> {newsPostId ? 'Edit Post' : 'New Post'}
              </h3>

              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Post Title</label>
                <input
                  type="text"
                  required
                  value={newsTitle}
                  onChange={e => setNewsTitle(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  placeholder="e.g. Minecraft 1.22 releases next week"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Type</label>
                  <select
                    value={newsType}
                    onChange={e => setNewsType(e.target.value)}
                    className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold cursor-pointer"
                  >
                    <option value="news">News</option>
                    <option value="upcoming">Upcoming</option>
                  </select>
                </div>
                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Topic</label>
                  <select
                    value={newsTopic}
                    onChange={e => setNewsTopic(e.target.value)}
                    className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold cursor-pointer"
                  >
                    {newsTopics.map(topic => (
                      <option key={topic} value={topic}>{topic}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Content (Markdown Supported)</label>
                <textarea
                  required
                  rows={8}
                  value={newsContent}
                  onChange={e => setNewsContent(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed"
                  placeholder="*   **What**: A new vanilla feature dropping soon...\n*   **When**: Expected this weekend..."
                />
              </div>

              <div>
                <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Link (optional — e.g. a YouTube video)</label>
                <input
                  type="url"
                  value={newsLink}
                  onChange={e => setNewsLink(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                  placeholder="https://youtube.com/watch?v=..."
                />
              </div>

              <div className="flex gap-2">
                {newsPostId && (
                  <button
                    type="button"
                    onClick={() => {
                      setNewsPostId('');
                      setNewsTitle('');
                      setNewsType('news');
                      if (newsTopics.length > 0) setNewsTopic(newsTopics[0]);
                      setNewsContent('');
                      setNewsLink('');
                    }}
                    className="flex-1 py-2.5 border border-sp-bronze text-xs font-bold uppercase rounded-lg text-text-warm/80 hover:text-white transition-all cursor-pointer"
                  >
                    Cancel
                  </button>
                )}
                <button
                  type="submit"
                  className="flex-1 py-2.5 bg-sp-gold hover:bg-white border border-transparent rounded-lg text-xs font-bold uppercase tracking-wider text-black transition-all cursor-pointer font-serif flex items-center justify-center gap-1"
                >
                  <Plus className="w-4 h-4" /> {newsPostId ? 'Save Post' : 'Publish Post'}
                </button>
              </div>
            </form>

            {/* TOPICS CRUD MANAGER PANEL */}
            <form onSubmit={handleNewsTopicCreateSubmit} className="bg-sp-dark border border-sp-bronze p-4 rounded-xl space-y-3 steampunk-glass">
              <h4 className="font-serif text-xs text-sp-gold uppercase tracking-wide border-b border-sp-bronze/10 pb-1.5 flex items-center gap-1.5">
                <Tag className="w-4 h-4 text-sp-copper" /> Manage Topics
              </h4>
              <div className="flex gap-2">
                <input
                  type="text"
                  required
                  value={newNewsTopicName}
                  onChange={e => setNewNewsTopicName(e.target.value)}
                  placeholder="New topic name..."
                  className="flex-1 bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-2.5 py-1.5 focus:outline-hidden focus:border-sp-gold"
                />
                <button
                  type="submit"
                  className="px-3 py-1.5 bg-sp-gold hover:bg-white text-black font-bold uppercase rounded-lg text-[10px] transition-all cursor-pointer font-serif shrink-0"
                >
                  Create
                </button>
              </div>
              <div className="flex flex-wrap gap-1.5 pt-1.5">
                {newsTopics.map(topic => (
                  <div key={topic} className="flex items-center gap-1.5 bg-neutral-950/40 border border-sp-bronze/20 px-2.5 py-0.5 rounded-md text-[9px] font-mono text-text-warm/80">
                    <span>{topic}</span>
                    {topic !== 'General' && (
                      <button
                        type="button"
                        onClick={() => handleNewsTopicDelete(topic)}
                        className="text-rose-400 hover:text-red-500 font-bold font-mono px-0.5"
                        title={`Delete topic ${topic}`}
                      >
                        ×
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </form>
          </div>

          {/* COLUMN 2: LIST OF POSTED NEWS/UPCOMING ITEMS */}
          <div className="lg:col-span-2 space-y-4">
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2 flex items-center gap-2">
              <Newspaper className="w-5 h-5 text-sp-copper" /> Posted Items ({newsPosts.length})
            </h3>

            {loadingNews ? (
              <div className="py-8 text-center text-xs text-text-warm/40 italic">Retrieving posts...</div>
            ) : newsPosts.length === 0 ? (
              <div className="p-8 text-center text-text-warm/40 border border-dashed border-sp-bronze/45 rounded-xl">
                No posts yet.
              </div>
            ) : (
              <div className="space-y-4">
                {newsPosts.map((post) => (
                  <div key={post.id} className="bg-sp-dark/95 border border-sp-bronze rounded-xl p-4 flex flex-col justify-between gap-4">
                    <div className="flex justify-between items-start gap-4 text-left">
                      <div className="text-left space-y-1 min-w-0">
                        <h4 className="font-serif text-sm font-bold text-white uppercase tracking-wide truncate">
                          {post.title}
                        </h4>
                        <div className="flex gap-2 items-center text-[9px] text-text-warm/50 font-mono flex-wrap">
                          <span className={`px-1.5 py-0.2 rounded uppercase border flex items-center gap-1 ${
                            post.type === 'upcoming'
                              ? 'bg-sp-copper/10 border-sp-copper/30 text-sp-copper'
                              : 'bg-sp-gold/10 border-sp-gold/30 text-sp-gold'
                          }`}>
                            {post.type === 'upcoming' && <Rocket className="w-2.5 h-2.5" />} {post.type === 'upcoming' ? 'Upcoming' : 'News'}
                          </span>
                          <span className="bg-neutral-950/40 border border-sp-bronze/20 px-1.5 py-0.2 rounded">{post.topic}</span>
                          <span>By {post.author}</span>
                          <span>{new Date(post.createdAt).toLocaleDateString()}</span>
                          {post.link && (
                            <a
                              href={post.link}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="flex items-center gap-1 text-sp-gold/70 hover:text-sp-gold underline underline-offset-2"
                              title={post.link}
                            >
                              <ExternalLink className="w-2.5 h-2.5" /> Link
                            </a>
                          )}
                        </div>
                      </div>

                      <div className="flex gap-1.5 shrink-0">
                        <button
                          onClick={() => handleNewsEdit(post)}
                          className="p-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded transition-all cursor-pointer"
                          title="Edit Post"
                        >
                          <Edit className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => handleNewsDelete(post.id)}
                          className="p-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded transition-all cursor-pointer"
                          title="Delete Post"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
        </div>
      ) : null}

      {effectiveTab === 'moderation' && canAccessTab('moderation') && <ProfileBiosPanel />}

      {/* ======= REPORTS INBOX TAB ======= */}
      {effectiveTab === 'reports' && (
        <div className="space-y-4 relative">
          <div className="flex items-center justify-between border-b border-sp-bronze/25 pb-3">
            <h3 className="font-serif text-lg text-rose-300 uppercase tracking-wide flex items-center gap-2"><Flag className="w-5 h-5 text-crimson" /> Anonymous Reports Inbox ({reports.length})</h3>
            <p className="text-[10px] text-text-warm/30 italic">Reporter identity is never stored.</p>
          </div>
          {reports.length === 0 ? (
            <div className="text-center py-16 text-text-warm/30 border border-dashed border-sp-bronze/30 rounded-xl">No reports submitted yet.</div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {reports.map(r => (
                <div key={r.id} className="bg-sp-dark/80 border border-crimson/30 rounded-xl p-5 space-y-4 flex flex-col justify-between">
                  <div className="space-y-3 text-left">
                    <div className="flex items-start justify-between gap-3">
                      <div className="space-y-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-[9px] px-2 py-0.5 bg-crimson/20 border border-crimson/40 text-rose-300 rounded uppercase font-mono font-bold">{r.category}</span>
                          <span className="text-[9px] text-text-warm/30 font-mono">{new Date(r.submittedAt).toLocaleString()}</span>
                        </div>
                        <p className="text-xs font-bold text-white mt-1">Target Player: <span className="text-sp-gold font-mono">{r.targetUsername}</span>{r.blockedByCount > 0 && (
                          <span title="Number of players who blocked this member in chat (names are not shown)" className="ml-2 text-[9px] px-2 py-0.5 rounded bg-amber-500/15 border border-amber-500/40 text-amber-300 font-mono font-bold uppercase">Blocked by {r.blockedByCount} {r.blockedByCount === 1 ? 'player' : 'players'}</span>
                        )}</p>
                      </div>
                      <button onClick={async () => {
                        if (!confirm('Dismiss this report?')) return;
                        const res = await fetch(`/api/reports?id=${r.id}`, { method: 'DELETE' });
                        const d = await res.json();
                        if (res.ok && d.reports) {
                          setReports(d.reports);
                        } else {
                          alert(d.error || 'Failed to delete report.');
                        }
                      }} className="p-1.5 bg-crimson/15 hover:bg-crimson border border-crimson/30 text-rose-300 hover:text-white rounded transition-all cursor-pointer shrink-0" title="Dismiss Report"><Trash2 className="w-3.5 h-3.5" /></button>
                    </div>
                    <p className="text-xs text-text-warm/70 leading-relaxed bg-neutral-950/40 rounded-lg p-3 border border-sp-bronze/10 break-words">{r.details}</p>
                  </div>

                  <div className="flex flex-wrap gap-2 pt-2 border-t border-sp-bronze/10">
                    <button
                      onClick={() => handleOpenActionModal(r.targetUsername, 'kick')}
                      className="px-2.5 py-1 bg-amber-900/20 hover:bg-amber-800/40 border border-amber-600/35 hover:border-amber-500 rounded text-[10px] uppercase font-bold text-amber-300 flex items-center gap-1 transition-all cursor-pointer"
                    >
                      <UserX className="w-3 h-3" /> Kick
                    </button>
                    <button
                      onClick={() => handleOpenActionModal(r.targetUsername, 'ban')}
                      className="px-2.5 py-1 bg-crimson/15 hover:bg-crimson/30 border border-crimson/40 hover:border-red-500 rounded text-[10px] uppercase font-bold text-rose-300 flex items-center gap-1 transition-all cursor-pointer"
                    >
                      <VolumeX className="w-3 h-3" /> Ban
                    </button>
                    <Link
                      href={`/chat?user=${r.targetUsername}`}
                      className="px-2.5 py-1 bg-sp-gold/10 hover:bg-sp-gold/25 border border-sp-gold/30 hover:border-sp-gold rounded text-[10px] uppercase font-bold text-sp-gold flex items-center gap-1 transition-all cursor-pointer"
                    >
                      <MessageSquare className="w-3 h-3" /> Message
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Quick Action Modal popup overlay */}
          {actionModalOpen && (
            <div className="fixed inset-0 bg-black/70 backdrop-blur-xs flex items-center justify-center p-4 z-50">
              <div className="bg-sp-dark border-2 border-sp-bronze p-6 rounded-xl w-full max-w-md shadow-2xl relative steampunk-glass space-y-4">
                <button
                  type="button"
                  onClick={() => setActionModalOpen(false)}
                  className="absolute top-4 right-4 text-text-warm/40 hover:text-white"
                >
                  <X className="w-5 h-5" />
                </button>

                <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2 flex items-center gap-2">
                  <ShieldAlert className="w-5 h-5 text-sp-copper" /> 
                  Sanction: {actionType === 'kick' ? `Kick ${siteContent.role_label_player}` : `Ban ${siteContent.role_label_player}`}
                </h3>

                <div className="text-xs space-y-1 text-left">
                  <p className="text-text-warm/60">Target: <span className="text-white font-bold font-mono">{actionTargetUsername}</span></p>
                  <p className="text-[10px] text-text-warm/40 italic">
                    {actionType === 'kick' 
                      ? 'This will disconnect the player instantly and log a kick warning.' 
                      : 'This will permanently deny server entry to this player. RCON console command will compile.'}
                  </p>
                </div>

                {actionSuccess ? (
                  <div className="bg-green-500/10 border border-green-500 text-green-400 text-xs px-3 py-2.5 rounded-lg flex items-center gap-2">
                    <ShieldCheck className="w-4 h-4" /> {actionSuccess}
                  </div>
                ) : (
                  <form onSubmit={handleModerationAction} className="space-y-4">
                    <div className="space-y-1 text-left">
                      <label className="block text-[9px] uppercase font-bold text-sp-gold/70">Sanction Reason</label>
                      <textarea
                        required
                        rows={3}
                        value={actionReason}
                        onChange={e => setActionReason(e.target.value)}
                        placeholder="Provide details for the logs & kick message..."
                        className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold resize-none leading-relaxed"
                      />
                    </div>

                    {actionError && <p className="text-xs text-rose-400 text-left">{actionError}</p>}

                    <div className="flex gap-3 pt-2">
                      <button
                        type="button"
                        onClick={() => setActionModalOpen(false)}
                        className="flex-1 py-2 border border-sp-bronze text-xs uppercase rounded-lg text-text-warm/60 hover:text-white transition-all cursor-pointer font-serif"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={actionSubmitting || !actionReason.trim()}
                        className={`flex-1 py-2 text-black font-bold uppercase text-xs rounded-lg hover:bg-white transition-all cursor-pointer font-serif flex items-center justify-center gap-2 ${
                          actionType === 'kick' ? 'bg-amber-500' : 'bg-crimson text-white'
                        }`}
                      >
                        {actionSubmitting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <>Execute {actionType}</>}
                      </button>
                    </div>
                  </form>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* ======= BUG REPORTS TAB ======= */}
      {effectiveTab === 'bugs' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between border-b border-sp-bronze/25 pb-3">
            <h3 className="font-serif text-lg text-rose-300 uppercase tracking-wide flex items-center gap-2"><Bug className="w-5 h-5 text-crimson" /> Bug Reports ({bugReports.length})</h3>
            <p className="text-[10px] text-text-warm/30 italic">Submitted from a pilot's own profile — identity is included. Members also see open/recently-fixed reports on the public /known-bugs page.</p>
          </div>

          <div className="flex items-start gap-2.5 bg-amber-950/30 border border-amber-700/30 rounded-lg p-3">
            <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
            <p className="text-[11px] text-amber-300/80 leading-relaxed">
              Marking a report as Fixed here does <strong>not</strong> post anything to the public Updates feed — add an Updates entry yourself (Updates tab) once a fix actually ships. Resolved reports drop into the Archive below automatically 14 days after resolution.
            </p>
          </div>

          <div className="flex flex-wrap gap-1.5">
            {[['active', 'Active'], ['archived', `Archive (${archivedBugCount})`]].map(([key, label]) => (
              <button
                key={key}
                onClick={() => setBugFilter(key)}
                className={`px-3 py-1 rounded text-[10px] font-bold uppercase transition-all cursor-pointer flex items-center gap-1.5 ${
                  bugFilter === key ? 'bg-sp-gold text-black' : 'bg-neutral-950/40 text-text-warm/60 border border-sp-bronze/20 hover:text-white'
                }`}
              >
                {key === 'archived' && <Archive className="w-3 h-3" />} {label}
              </button>
            ))}
          </div>

          {visibleBugReports.length === 0 ? (
            <div className="text-center py-16 text-text-warm/30 border border-dashed border-sp-bronze/30 rounded-xl">
              {bugFilter === 'archived' ? 'Nothing archived yet.' : 'No active bug reports.'}
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {visibleBugReports.map(r => (
                <div key={r.id} className="bg-sp-dark/80 border border-crimson/30 rounded-xl p-5 space-y-3 flex flex-col justify-between text-left">
                  <div className="space-y-2">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2 flex-wrap mb-1">
                          <span className={`text-[9px] px-2 py-0.5 rounded uppercase font-mono font-bold border ${
                            r.status === 'fixed'
                              ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-400'
                              : r.status === 'wont_fix'
                              ? 'bg-neutral-700/40 border-neutral-500/40 text-text-warm/60'
                              : r.status === 'duplicate'
                              ? 'bg-sp-copper/20 border-sp-copper/40 text-sp-copper'
                              : 'bg-crimson/20 border-crimson/40 text-rose-300'
                          }`}>
                            {BUG_STATUS_LABELS[r.status] || r.status}
                          </span>
                          {bugCategoryDrafts[r.id] ? (
                            <div className="flex items-center gap-1">
                              <select
                                value={bugCategoryDrafts[r.id].category}
                                onChange={e => {
                                  const newCat = e.target.value;
                                  if (newCat === 'other') {
                                    setBugCategoryDrafts(prev => ({ ...prev, [r.id]: { category: newCat, customCategory: prev[r.id]?.customCategory || '' } }));
                                  } else {
                                    saveBugCategory(r.id, newCat, null);
                                  }
                                }}
                                className="text-[9px] bg-neutral-950 border border-sp-bronze/40 rounded px-1.5 py-0.5 text-white cursor-pointer focus:outline-hidden focus:border-sp-gold"
                              >
                                {BUG_CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                              </select>
                              {bugCategoryDrafts[r.id].category === 'other' && (
                                <>
                                  <input
                                    type="text"
                                    value={bugCategoryDrafts[r.id].customCategory}
                                    onChange={e => setBugCategoryDrafts(prev => ({ ...prev, [r.id]: { ...prev[r.id], customCategory: e.target.value } }))}
                                    placeholder="Describe it"
                                    className="text-[9px] bg-neutral-950 border border-sp-bronze/40 rounded px-1.5 py-0.5 text-white w-24 focus:outline-hidden focus:border-sp-gold"
                                  />
                                  <button
                                    onClick={() => saveBugCategory(r.id, 'other', bugCategoryDrafts[r.id].customCategory)}
                                    disabled={!bugCategoryDrafts[r.id].customCategory?.trim() || bugCategorySavingId === r.id}
                                    className="text-[9px] px-1.5 py-0.5 bg-sp-gold/20 hover:bg-sp-gold/30 border border-sp-gold/40 rounded text-sp-gold cursor-pointer disabled:opacity-40"
                                  >
                                    Save
                                  </button>
                                </>
                              )}
                              <button
                                onClick={() => setBugCategoryDrafts(prev => { const next = { ...prev }; delete next[r.id]; return next; })}
                                className="text-[9px] text-text-warm/40 hover:text-white cursor-pointer px-0.5"
                                title="Cancel"
                              >
                                <X className="w-3 h-3" />
                              </button>
                            </div>
                          ) : (
                            <span
                              onClick={() => setBugCategoryDrafts(prev => ({ ...prev, [r.id]: { category: r.category, customCategory: r.customCategory || '' } }))}
                              title="Click to change category"
                              className="text-[9px] px-2 py-0.5 rounded uppercase font-mono font-bold border bg-sp-bronze/10 border-sp-bronze/30 text-text-warm/60 hover:border-sp-gold hover:text-sp-gold cursor-pointer flex items-center gap-1"
                            >
                              {bugCategoryLabel(r.category, r.customCategory)} <Edit className="w-2.5 h-2.5" />
                            </span>
                          )}
                          {r.affectedCount > 1 && (
                            <span className="text-[9px] px-2 py-0.5 rounded uppercase font-mono font-bold border bg-sp-gold/10 border-sp-gold/30 text-sp-gold">
                              +{r.affectedCount - 1} also affected
                            </span>
                          )}
                          <span className="text-[9px] text-text-warm/30 font-mono">{new Date(r.submittedAt).toLocaleString()}</span>
                        </div>
                        <p className="text-xs font-bold text-white">{r.subject}</p>
                        <p className="text-[10px] text-text-warm/40 font-mono">by {r.reporterUsername} ({r.reporterEmail})</p>
                        {r.status === 'duplicate' && r.duplicateOf && (
                          <p className="text-[10px] text-sp-copper/80 font-mono">→ duplicate of #{r.duplicateOf}</p>
                        )}
                      </div>
                      <button
                        onClick={async () => { if (!confirm('Delete this bug report?')) return; const res = await fetch(`/api/bug-reports?id=${r.id}`, { method: 'DELETE' }); const d = await res.json(); if (d.reports) setBugReports(d.reports); }}
                        className="p-1.5 bg-crimson/15 hover:bg-crimson border border-crimson/30 text-rose-300 hover:text-white rounded transition-all cursor-pointer shrink-0"
                        title="Delete Report"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                    <p className="text-xs text-text-warm/70 leading-relaxed bg-neutral-950/40 rounded-lg p-3 border border-sp-bronze/10 break-words">{r.description}</p>

                    {/* Staff-only notes — never shown to the reporter or on
                        /known-bugs. The idea: next time this bug resurfaces,
                        whoever picks it up already knows where to look. */}
                    <div className="space-y-1">
                      <label className="flex items-center gap-1.5 text-[9px] uppercase font-bold text-sp-gold/60"><StickyNote className="w-3 h-3" /> Staff Notes (private)</label>
                      <textarea
                        rows={2}
                        value={bugNotesDrafts[r.id] ?? r.staffNotes ?? ''}
                        onChange={e => setBugNotesDrafts(prev => ({ ...prev, [r.id]: e.target.value }))}
                        placeholder="Cause, fix location, anything worth remembering if this comes back..."
                        className="w-full bg-neutral-950/60 border border-sp-bronze/20 text-text-warm/80 text-[11px] rounded-lg px-2.5 py-2 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-none"
                      />
                      {bugNotesDrafts[r.id] !== undefined && bugNotesDrafts[r.id] !== (r.staffNotes ?? '') && (
                        <button
                          onClick={() => saveBugNotes(r.id)}
                          disabled={bugNotesSavingId === r.id}
                          className="px-2 py-0.5 bg-sp-gold/15 hover:bg-sp-gold/25 border border-sp-gold/30 rounded text-[9px] uppercase font-bold text-sp-gold cursor-pointer disabled:opacity-50"
                        >
                          {bugNotesSavingId === r.id ? 'Saving...' : 'Save Notes'}
                        </button>
                      )}
                    </div>

                    {/* Conversation thread toggle */}
                    <button
                      onClick={() => toggleBugThread(r.id)}
                      className="text-[10px] uppercase font-bold text-text-warm/50 hover:text-sp-gold flex items-center gap-1 cursor-pointer"
                    >
                      <MessageSquare className="w-3 h-3" /> {expandedBugId === r.id ? 'Hide' : 'View'} Conversation {bugThreads[r.id] ? `(${bugThreads[r.id].length})` : ''}
                      {expandedBugId === r.id ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                    </button>
                    {expandedBugId === r.id && (
                      <div className="space-y-1.5 bg-neutral-950/30 border border-sp-bronze/10 rounded-lg p-2.5">
                        {(bugThreads[r.id] || []).length === 0 ? (
                          <p className="text-[10px] text-text-warm/30 italic">No messages yet.</p>
                        ) : (bugThreads[r.id] || []).map(m => (
                          <div key={m.id} className={`text-[11px] rounded-lg p-2 max-w-[90%] ${m.senderType === 'staff' ? 'bg-sp-gold/10 border border-sp-gold/25 ml-auto' : 'bg-neutral-900/60 border border-sp-bronze/20'}`}>
                            <p className="text-[8px] uppercase font-bold text-text-warm/40 mb-0.5">
                              {m.senderType === 'staff' ? `Staff (${m.senderName || 'Staff'})` : (m.senderName || 'Reporter')}
                              {m.deliveryChannel && <span className="ml-1 text-sp-gold/60">via {m.deliveryChannel}</span>}
                            </p>
                            <p className="text-text-warm/85 whitespace-pre-wrap break-words">{m.body}</p>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="flex flex-wrap gap-2 pt-2 border-t border-sp-bronze/10">
                    {['open', 'fixed', 'wont_fix'].map(status => (
                      <button
                        key={status}
                        disabled={r.status === status}
                        onClick={async () => {
                          const res = await fetch('/api/bug-reports', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ action: 'update_status', id: r.id, status })
                          });
                          const d = await res.json();
                          if (d.reports) setBugReports(d.reports);
                        }}
                        className="px-2.5 py-1 bg-sp-bronze/10 hover:bg-sp-bronze/25 disabled:opacity-30 disabled:cursor-not-allowed border border-sp-bronze/30 hover:border-sp-gold rounded text-[10px] uppercase font-bold text-text-warm/70 hover:text-sp-gold flex items-center gap-1 transition-all cursor-pointer"
                      >
                        Mark {status === 'wont_fix' ? "Won't Fix" : status}
                      </button>
                    ))}
                    <button
                      onClick={() => { setDuplicateTarget({ id: r.id, subject: r.subject }); setDuplicateOfId(''); }}
                      disabled={r.status === 'duplicate'}
                      className="px-2.5 py-1 bg-sp-bronze/10 hover:bg-sp-bronze/25 disabled:opacity-30 border border-sp-bronze/30 hover:border-sp-copper rounded text-[10px] uppercase font-bold text-text-warm/70 hover:text-sp-copper flex items-center gap-1 transition-all cursor-pointer"
                    >
                      Mark Duplicate
                    </button>
                    <button
                      onClick={() => { setContactTarget({ id: r.id, subject: r.subject }); setContactChannel('bell'); setContactMessage(''); setContactError(''); }}
                      className="px-2.5 py-1 bg-sp-gold/10 hover:bg-sp-gold/25 border border-sp-gold/30 rounded text-[10px] uppercase font-bold text-sp-gold flex items-center gap-1 transition-all cursor-pointer ml-auto"
                    >
                      <Send className="w-3 h-3" /> Contact Reporter
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Contact Reporter modal */}
          {contactTarget && (
            <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4 backdrop-blur-sm">
              <form onSubmit={handleContactSubmit} className="w-full max-w-md bg-sp-dark border-2 border-sp-gold p-6 rounded-xl relative steampunk-border shadow-2xl space-y-4">
                <button type="button" onClick={() => setContactTarget(null)} className="absolute top-4 right-4 text-text-warm/60 hover:text-white cursor-pointer"><X className="w-5 h-5" /></button>
                <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-gold/25 pb-2 flex items-center gap-2">
                  <Send className="w-5 h-5" /> Contact Reporter
                </h3>
                <p className="text-xs text-text-warm/60">Re: &quot;{contactTarget.subject}&quot;</p>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1.5">Deliver via</label>
                  <div className="flex gap-2">
                    {[['bell', Bell, 'Bell'], ['chat', MessageSquare, 'Chat'], ['email', Mail, 'Email']].map(([key, Icon, label]) => (
                      <button
                        key={key}
                        type="button"
                        onClick={() => setContactChannel(key)}
                        className={`flex-1 py-2 rounded-lg border text-[10px] uppercase font-bold flex flex-col items-center gap-1 cursor-pointer transition-all ${
                          contactChannel === key ? 'bg-sp-gold/20 border-sp-gold text-sp-gold' : 'bg-neutral-950/40 border-sp-bronze/20 text-text-warm/60 hover:text-white'
                        }`}
                      >
                        <Icon className="w-4 h-4" /> {label}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Message</label>
                  <textarea
                    required
                    rows={4}
                    value={contactMessage}
                    onChange={e => setContactMessage(e.target.value)}
                    placeholder="What do you need from them, or what's the update?"
                    className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-none"
                  />
                </div>

                {contactError && <p className="text-xs text-rose-400">{contactError}</p>}

                <div className="flex justify-end gap-3 pt-1">
                  <button type="button" onClick={() => setContactTarget(null)} className="px-4 py-2 border border-sp-bronze text-xs font-bold uppercase rounded-lg text-text-warm/80 hover:text-white cursor-pointer">Cancel</button>
                  <button type="submit" disabled={contactSending || !contactMessage.trim()} className="px-5 py-2 bg-sp-gold border border-sp-gold rounded-lg text-xs font-bold uppercase tracking-wider text-black hover:bg-white cursor-pointer font-serif disabled:opacity-50">
                    {contactSending ? 'Sending...' : 'Send'}
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* Mark Duplicate modal */}
          {duplicateTarget && (
            <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4 backdrop-blur-sm">
              <form onSubmit={handleMarkDuplicate} className="w-full max-w-md bg-sp-dark border-2 border-sp-copper p-6 rounded-xl relative steampunk-border shadow-2xl space-y-4">
                <button type="button" onClick={() => setDuplicateTarget(null)} className="absolute top-4 right-4 text-text-warm/60 hover:text-white cursor-pointer"><X className="w-5 h-5" /></button>
                <h3 className="font-serif text-lg text-sp-copper uppercase tracking-wide border-b border-sp-copper/25 pb-2">Mark as Duplicate</h3>
                <p className="text-xs text-text-warm/60">&quot;{duplicateTarget.subject}&quot; is a duplicate of:</p>
                <select
                  required
                  value={duplicateOfId}
                  onChange={e => setDuplicateOfId(e.target.value)}
                  className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold cursor-pointer"
                >
                  <option value="" disabled>Pick the canonical report...</option>
                  {bugReports.filter(r => r.id !== duplicateTarget.id && r.status !== 'duplicate').map(r => (
                    <option key={r.id} value={r.id}>{r.subject} ({BUG_STATUS_LABELS[r.status] || r.status})</option>
                  ))}
                </select>
                <div className="flex justify-end gap-3 pt-1">
                  <button type="button" onClick={() => setDuplicateTarget(null)} className="px-4 py-2 border border-sp-bronze text-xs font-bold uppercase rounded-lg text-text-warm/80 hover:text-white cursor-pointer">Cancel</button>
                  <button type="submit" disabled={!duplicateOfId} className="px-5 py-2 bg-sp-copper border border-sp-copper rounded-lg text-xs font-bold uppercase tracking-wider text-black hover:bg-white cursor-pointer font-serif disabled:opacity-50">Confirm</button>
                </div>
              </form>
            </div>
          )}
        </div>
      )}

      {/* ======= STAFF APPLICATIONS TAB ======= */}
      {effectiveTab === 'applications' && (
        <div className="space-y-4 relative">
          <div className="flex items-center justify-between border-b border-sp-bronze/25 pb-3">
            <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2"><ShieldCheck className="w-5 h-5 text-sp-copper" /> Staff Applications ({staffApplications.length})</h3>
            <p className="text-[10px] text-text-warm/30 italic">Applying for the Moderator role — an unpaid, volunteer position.</p>
          </div>

          {staffApplications.length === 0 ? (
            <div className="text-center py-16 text-text-warm/30 border border-dashed border-sp-bronze/30 rounded-xl">No staff applications submitted yet.</div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {staffApplications.map(a => (
                <div key={a.id} className="bg-sp-dark/80 border border-sp-bronze/30 rounded-xl p-5 space-y-3 flex flex-col justify-between text-left">
                  <div className="space-y-2">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="text-sm font-bold text-white">{a.applicantUsername || a.applicantEmail}</p>
                        <p className="text-[10px] text-text-warm/40 font-mono">{a.applicantEmail} &middot; age {a.age} &middot; {new Date(a.createdAt).toLocaleString()}</p>
                      </div>
                      <span className={`text-[9px] px-2 py-0.5 rounded uppercase font-mono font-bold border shrink-0 ${
                        a.status === 'approved' ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-400'
                          : a.status === 'declined' ? 'bg-crimson/20 border-crimson/40 text-rose-300'
                          : 'bg-sp-gold/20 border-sp-gold/40 text-sp-gold'
                      }`}>
                        {a.status}
                      </span>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-[11px]">
                      <p><span className="text-text-warm/40 uppercase font-bold text-[9px] block">Availability</span>{a.availability}</p>
                      {a.timezone && <p><span className="text-text-warm/40 uppercase font-bold text-[9px] block">Timezone</span>{a.timezone}</p>}
                      <p><span className="text-text-warm/40 uppercase font-bold text-[9px] block">Reference</span>{a.referenceName}</p>
                      {a.discordUsername && <p><span className="text-text-warm/40 uppercase font-bold text-[9px] block">Discord</span>{a.discordUsername}</p>}
                    </div>

                    <div className="space-y-1.5">
                      <div>
                        <p className="text-text-warm/40 uppercase font-bold text-[9px]">Why they want to join staff</p>
                        <p className="text-xs text-text-warm/70 leading-relaxed bg-neutral-950/40 rounded-lg p-2.5 border border-sp-bronze/10 break-words">{a.reason}</p>
                      </div>
                      <div>
                        <p className="text-text-warm/40 uppercase font-bold text-[9px]">What they can add</p>
                        <p className="text-xs text-text-warm/70 leading-relaxed bg-neutral-950/40 rounded-lg p-2.5 border border-sp-bronze/10 break-words">{a.contribution}</p>
                      </div>
                      {a.experience && (
                        <div>
                          <p className="text-text-warm/40 uppercase font-bold text-[9px]">Prior experience</p>
                          <p className="text-xs text-text-warm/70 leading-relaxed bg-neutral-950/40 rounded-lg p-2.5 border border-sp-bronze/10 break-words">{a.experience}</p>
                        </div>
                      )}
                    </div>

                    <div>
                      <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Staff Notes (internal)</label>
                      <textarea
                        rows={2}
                        value={saNotesDrafts[a.id] ?? (a.staffNotes || '')}
                        onChange={e => setSaNotesDrafts(prev => ({ ...prev, [a.id]: e.target.value }))}
                        onBlur={() => { if ((saNotesDrafts[a.id] ?? '') !== (a.staffNotes || '')) saveApplicationNotes(a.id); }}
                        placeholder="Interview notes, concerns, etc. — never shown to the applicant"
                        className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-none"
                      />
                    </div>
                  </div>

                  <div className="flex flex-wrap gap-2 pt-2 border-t border-sp-bronze/10">
                    <button
                      onClick={() => setSaContactTarget({ id: a.id, applicantUsername: a.applicantUsername || a.applicantEmail })}
                      className="px-2.5 py-1 bg-sp-gold/10 hover:bg-sp-gold/25 border border-sp-gold/30 hover:border-sp-gold rounded text-[10px] uppercase font-bold text-sp-gold flex items-center gap-1 transition-all cursor-pointer"
                    >
                      <Send className="w-3 h-3" /> Contact
                    </button>
                    {a.status !== 'approved' && (
                      <button
                        onClick={() => decideApplication(a.id, 'approved')}
                        disabled={saDecidingId === a.id}
                        className="px-2.5 py-1 bg-emerald-900/20 hover:bg-emerald-800/40 border border-emerald-600/35 hover:border-emerald-500 rounded text-[10px] uppercase font-bold text-emerald-300 flex items-center gap-1 transition-all cursor-pointer disabled:opacity-50"
                      >
                        <Check className="w-3 h-3" /> Approve
                      </button>
                    )}
                    {a.status !== 'declined' && (
                      <button
                        onClick={() => decideApplication(a.id, 'declined')}
                        disabled={saDecidingId === a.id}
                        className="px-2.5 py-1 bg-crimson/15 hover:bg-crimson/30 border border-crimson/40 hover:border-red-500 rounded text-[10px] uppercase font-bold text-rose-300 flex items-center gap-1 transition-all cursor-pointer disabled:opacity-50"
                      >
                        <X className="w-3 h-3" /> Decline
                      </button>
                    )}
                    <button
                      onClick={async () => {
                        if (!confirm('Remove this application from the list? This cannot be undone.')) return;
                        const res = await fetch(`/api/staff-applications?id=${a.id}`, { method: 'DELETE' });
                        const d = await res.json();
                        if (res.ok && d.applications) setStaffApplications(d.applications);
                        else alert(d.error || 'Failed to delete application.');
                      }}
                      className="ml-auto p-1.5 bg-neutral-800/40 hover:bg-crimson border border-sp-bronze/30 hover:border-crimson text-text-warm/50 hover:text-white rounded transition-all cursor-pointer shrink-0"
                      title="Remove"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Contact Applicant modal */}
          {saContactTarget && (
            <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4 backdrop-blur-sm">
              <form onSubmit={handleApplicationContactSubmit} className="w-full max-w-md bg-sp-dark border-2 border-sp-gold p-6 rounded-xl relative steampunk-border shadow-2xl space-y-4">
                <button type="button" onClick={() => setSaContactTarget(null)} className="absolute top-4 right-4 text-text-warm/60 hover:text-white cursor-pointer"><X className="w-5 h-5" /></button>
                <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-gold/25 pb-2 flex items-center gap-2">
                  <Send className="w-5 h-5" /> Contact Applicant
                </h3>
                <p className="text-xs text-text-warm/60">Re: {saContactTarget.applicantUsername}&apos;s Staff Application</p>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1.5">Deliver via</label>
                  <div className="flex gap-2">
                    {[['bell', Bell, 'Bell'], ['chat', MessageSquare, 'Chat'], ['email', Mail, 'Email']].map(([key, Icon, label]) => (
                      <button
                        key={key}
                        type="button"
                        onClick={() => setSaContactChannel(key)}
                        className={`flex-1 py-2 rounded-lg border text-[10px] uppercase font-bold flex flex-col items-center gap-1 cursor-pointer transition-all ${
                          saContactChannel === key ? 'bg-sp-gold/20 border-sp-gold text-sp-gold' : 'bg-neutral-950/40 border-sp-bronze/20 text-text-warm/60 hover:text-white'
                        }`}
                      >
                        <Icon className="w-4 h-4" /> {label}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-[9px] uppercase font-bold text-sp-gold/70 mb-1">Message</label>
                  <textarea
                    required
                    rows={4}
                    value={saContactMessage}
                    onChange={e => setSaContactMessage(e.target.value)}
                    placeholder="e.g. an interview time, or a follow-up question"
                    className="w-full bg-neutral-900 border border-sp-bronze/40 text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed resize-none"
                  />
                </div>

                {saContactError && <p className="text-xs text-rose-400">{saContactError}</p>}

                <div className="flex justify-end gap-3 pt-1">
                  <button type="button" onClick={() => setSaContactTarget(null)} className="px-4 py-2 border border-sp-bronze text-xs font-bold uppercase rounded-lg text-text-warm/80 hover:text-white cursor-pointer">Cancel</button>
                  <button type="submit" disabled={saContactSending || !saContactMessage.trim()} className="px-5 py-2 bg-sp-gold border border-sp-gold rounded-lg text-xs font-bold uppercase tracking-wider text-black hover:bg-white cursor-pointer font-serif disabled:opacity-50">
                    {saContactSending ? 'Sending...' : 'Send'}
                  </button>
                </div>
              </form>
            </div>
          )}
        </div>
      )}

      {/* ======= ANALYTICS TAB (website + app usage) ======= */}
      {effectiveTab === 'analytics' && canAccessTab('analytics') && <AnalyticsTab />}

      {/* ======= STORY ROOM TAB (staff storyline workspace) ======= */}
      {effectiveTab === 'story' && canAccessTab('story') && (
        <StoryRoomTab canPublishToWiki={canAccessTab('wiki')} isAdmin={isAdmin} />
      )}

      {/* ======= ACCESS CONTROL TAB ======= */}
      {effectiveTab === 'access' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between border-b border-sp-bronze/25 pb-3">
            <h3 className="font-serif text-lg text-rose-300 uppercase tracking-wide flex items-center gap-2"><Ban className="w-5 h-5 text-crimson" /> Access Control</h3>
            <button onClick={fetchAccessList} className="text-text-warm/40 hover:text-sp-gold cursor-pointer" title="Refresh"><RefreshCw className="w-4 h-4" /></button>
          </div>

          <div className="flex items-start gap-2.5 bg-amber-950/30 border border-amber-700/30 rounded-lg p-3">
            <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
            <p className="text-[11px] text-amber-300/80 leading-relaxed">
              Mirrored live from the server&apos;s real ban list and whitelist (synced by the plugin every ~30s) — staff-only, never shown on the public Members page, which only ever lists real website accounts.
            </p>
          </div>

          {/* Banned Players */}
          <div className="space-y-2">
            <h4 className="text-xs font-bold text-rose-300 uppercase tracking-wide flex items-center gap-2"><Ban className="w-3.5 h-3.5" /> Banned Players ({accessList.banned.length})</h4>
            {accessList.banned.length === 0 ? (
              <div className="text-center py-8 text-text-warm/30 border border-dashed border-sp-bronze/30 rounded-xl text-xs">No bans on record.</div>
            ) : (
              <div className="overflow-x-auto border border-sp-bronze/20 rounded-xl">
                <table className="w-full text-left text-xs divide-y divide-sp-bronze/10">
                  <thead>
                    <tr className="text-text-warm/50 font-bold uppercase text-[9px] tracking-wider bg-neutral-950/30">
                      <th className="py-2.5 px-3">Player</th>
                      <th className="py-2.5 px-3">Reason</th>
                      <th className="py-2.5 px-3">Banned By</th>
                      <th className="py-2.5 px-3">Date</th>
                      <th className="py-2.5 px-3">Expires</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-sp-bronze/10">
                    {accessList.banned.map(b => (
                      <tr key={b.uuid} className="hover:bg-neutral-950/20">
                        <td className="py-2.5 px-3 flex items-center gap-2">
                          <PlayerAvatar mcUsername={b.username} size={20} className="rounded" />
                          <span className="text-white font-bold">{b.username}</span>
                        </td>
                        <td className="py-2.5 px-3 text-text-warm/70">{b.reason || '—'}</td>
                        <td className="py-2.5 px-3 text-text-warm/50 font-mono">{b.source || '—'}</td>
                        <td className="py-2.5 px-3 text-text-warm/50 font-mono">{b.created || '—'}</td>
                        <td className="py-2.5 px-3 text-text-warm/50 font-mono">{b.expires || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Whitelisted, no website account */}
          <div className="space-y-2">
            <h4 className="text-xs font-bold text-sp-gold uppercase tracking-wide flex items-center gap-2">
              <UserCheck className="w-3.5 h-3.5" /> Whitelisted — No Website Account ({accessList.whitelistedUnregistered.length})
            </h4>
            <p className="text-[10px] text-text-warm/40">{accessList.whitelistedTotal} whitelisted in total; the rest already have a matching website account.</p>
            {accessList.whitelistedUnregistered.length === 0 ? (
              <div className="text-center py-8 text-text-warm/30 border border-dashed border-sp-bronze/30 rounded-xl text-xs">Every whitelisted player has a website account.</div>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2">
                {accessList.whitelistedUnregistered.map(w => (
                  <div key={w.uuid} className="flex items-center gap-2 bg-neutral-900/30 border border-sp-bronze/20 rounded-lg px-2.5 py-2">
                    <PlayerAvatar mcUsername={w.username} size={20} className="rounded shrink-0" />
                    <span className="text-xs font-bold text-white truncate">{w.username}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ======= CONSOLE TAB (admin-only raw RCON terminal) ======= */}
      {effectiveTab === 'console' && canAccessTab('console') && (
        <div className="space-y-4">
          <div className="flex items-center justify-between border-b border-sp-bronze/25 pb-3">
            <h3 className="font-serif text-lg text-rose-300 uppercase tracking-wide flex items-center gap-2"><Terminal className="w-5 h-5 text-crimson" /> Console</h3>
            <button onClick={fetchConsoleLog} className="text-text-warm/40 hover:text-sp-gold cursor-pointer" title="Refresh"><RefreshCw className="w-4 h-4" /></button>
          </div>

          <div className="flex items-start gap-2.5 bg-amber-950/30 border border-amber-700/30 rounded-lg p-3">
            <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
            <p className="text-[11px] text-amber-300/80 leading-relaxed">
              Sends any command straight to the live Minecraft server console over RCON — unlike the templated kick/ban actions elsewhere in this panel, there is no undo. Every command run here, and its exact server reply, is logged below permanently with your name attached.
            </p>
          </div>

          <div className="border border-sp-bronze/30 rounded-xl bg-black/60 font-mono text-xs overflow-hidden">
            <div className="max-h-96 overflow-y-auto p-3 space-y-2.5 flex flex-col-reverse">
              {consoleLog.length === 0 ? (
                <div className="text-text-warm/30 text-center py-6">No commands run yet.</div>
              ) : (
                consoleLog.map(entry => (
                  <div key={entry.id} className="border-b border-sp-bronze/10 pb-2 last:border-0">
                    <div className="flex items-center gap-2 text-[10px] text-text-warm/40">
                      <span className="text-sp-gold/70">{entry.staffMember}</span>
                      <span>{new Date(entry.date).toLocaleString()}</span>
                    </div>
                    <div className="text-emerald-400">$ {entry.command}</div>
                    {entry.response && (
                      <div className="text-text-warm/60 whitespace-pre-wrap break-words">{entry.response}</div>
                    )}
                  </div>
                ))
              )}
            </div>
            <form onSubmit={handleConsoleSend} className="flex items-center gap-2 border-t border-sp-bronze/30 bg-neutral-950/60 px-3 py-2.5">
              <span className="text-emerald-400 font-bold">$</span>
              <input
                type="text"
                value={consoleInput}
                onChange={e => setConsoleInput(e.target.value)}
                placeholder="e.g. cmi money give SomePlayer 100"
                disabled={consoleSending}
                className="flex-1 bg-transparent text-white placeholder:text-text-warm/25 focus:outline-hidden disabled:opacity-50"
                autoComplete="off"
                spellCheck={false}
              />
              <button
                type="submit"
                disabled={consoleSending || !consoleInput.trim()}
                className="px-3 py-1.5 bg-crimson/20 hover:bg-crimson/35 border border-crimson/40 rounded text-[10px] uppercase font-bold text-rose-300 transition-all cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {consoleSending ? 'Sending…' : 'Send'}
              </button>
            </form>
          </div>

          {consoleError && <p className="text-xs text-rose-400">{consoleError}</p>}
        </div>
      )}

      {/* ======= SETTINGS TAB ======= */}
      {effectiveTab === 'settings' && canAccessTab('settings') && (
        <div className="space-y-6">
          <div className="border-b border-sp-bronze/25 pb-3">
            <div className="flex items-center justify-between">
              <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide flex items-center gap-2">
                <Settings className="w-5 h-5 text-sp-copper" /> Settings Command Center
              </h3>
            </div>
            <p className="text-[10px] text-text-warm/30 italic mt-1">Site-wide configuration tools — each one below is its own permission (Settings &gt; Roles &amp; Permissions), so you may only see some of these depending on your role. Roles &amp; Permissions, Newsletter, and Shared Files always stay admin-only.</p>
          </div>

          {/* Sub Tab Rendering */}
          <div className="pt-2">
            {effectiveSettingsSubTab === 'general' && (
              <div className="max-w-2xl text-left space-y-4">
                <p className="text-xs text-text-warm/60">Configure core server properties and display configurations.</p>
                <form onSubmit={async (e) => {
                  e.preventDefault();
                  setError(''); setSuccessMsg('');
                  try {
                    const res = await fetch('/api/settings', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ java_version: settingsJavaVersion, map_url: settingsMapUrl, server_address: settingsServerAddress, role: user.role }) });
                    const data = await res.json();
                    if (res.ok) { setSuccessMsg('Settings saved!'); setSettingsSaved(true); setTimeout(() => { setSuccessMsg(''); setSettingsSaved(false); }, 3000); }
                    else setError(data.error);
                  } catch { setError('Network error'); }
                }} className="bg-sp-dark border border-sp-bronze p-6 rounded-xl space-y-6 steampunk-glass">
                  <div className="space-y-2">
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/80">Server Address</label>
                    <p className="text-[10px] text-text-warm/40">The public join IP:port — shown on the homepage, footer, and registration flow, and used to query live server status. Format: <code>ip:port</code>.</p>
                    <input value={settingsServerAddress} onChange={e => setSettingsServerAddress(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold font-mono" placeholder="e.g. play.iekcorbar.com" />
                  </div>
                  <div className="space-y-2">
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/80">Java Version</label>
                    <p className="text-[10px] text-text-warm/40">Displayed as a badge on the homepage hero section.</p>
                    <input value={settingsJavaVersion} onChange={e => setSettingsJavaVersion(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold" placeholder="e.g. 25" />
                  </div>
                  <div className="space-y-2">
                    <label className="block text-[9px] uppercase font-bold text-sp-gold/80">Live Map URL</label>
                    <p className="text-[10px] text-text-warm/40">Players will be directed to this URL from the Map page. Leave empty to show a placeholder.</p>
                    <input type="url" value={settingsMapUrl} onChange={e => setSettingsMapUrl(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold" placeholder="https://map.yourserver.com" />
                  </div>
                  <button type="submit" className={`px-6 py-3 font-bold uppercase text-sm rounded-lg transition-all cursor-pointer font-serif flex items-center gap-2 ${settingsSaved ? 'bg-green-700 border-green-600 text-white' : 'bg-sp-gold hover:bg-white text-black border border-transparent'}`}>
                    <Settings className="w-4 h-4" /> {settingsSaved ? 'Saved!' : 'Save Settings'}
                  </button>
                </form>
              </div>
            )}

            {effectiveSettingsSubTab === 'carousel' && (
              <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
                {/* COLUMN 1: SETS SIDEBAR */}
                <div className="lg:col-span-1 space-y-4">
                  <h3 className="font-serif text-sm text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2">
                    Screenshot Sets
                  </h3>
                  <p className="text-[10px] text-text-warm/50 leading-relaxed">
                    The homepage carousel draws a random sample (max 10) from whichever set is <span className="text-emerald-400 font-bold">Active</span>. Each set can hold unlimited screenshots.
                  </p>

                  <form onSubmit={handleCreateSet} className="flex gap-1.5">
                    <input
                      type="text"
                      value={newSetName}
                      onChange={e => setNewSetName(e.target.value)}
                      placeholder="New set name..."
                      className="flex-1 min-w-0 bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                    />
                    <button
                      type="submit"
                      disabled={creatingSet || !newSetName.trim()}
                      className="px-2.5 bg-sp-gold hover:bg-white disabled:opacity-40 text-black rounded-lg transition-all cursor-pointer shrink-0"
                      title="Create set"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                  </form>

                  {carouselSetError && <p className="text-[10px] text-rose-400">{carouselSetError}</p>}

                  <div className="space-y-2">
                    {carouselSets.length === 0 ? (
                      <div className="p-4 text-center text-[10px] text-text-warm/40 border border-dashed border-sp-bronze/45 rounded-xl">
                        No sets yet. Create one above to start uploading screenshots.
                      </div>
                    ) : (
                      carouselSets.map((set) => (
                        <div
                          key={set.id}
                          onClick={() => setSelectedCarouselSetId(set.id)}
                          className={`p-3 rounded-lg border cursor-pointer transition-all space-y-1.5 ${
                            selectedCarouselSetId === set.id
                              ? 'bg-sp-gold/10 border-sp-gold'
                              : 'bg-neutral-900/30 border-sp-bronze/30 hover:border-sp-bronze'
                          }`}
                        >
                          <div className="flex items-center justify-between gap-2">
                            {renamingSetId === set.id ? (
                              <input
                                autoFocus
                                type="text"
                                value={renameSetName}
                                onChange={e => setRenameSetName(e.target.value)}
                                onClick={e => e.stopPropagation()}
                                onBlur={() => handleRenameSet(set.id)}
                                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); handleRenameSet(set.id); } if (e.key === 'Escape') setRenamingSetId(null); }}
                                className="flex-1 min-w-0 bg-neutral-950 border border-sp-gold text-white text-xs rounded px-2 py-1"
                              />
                            ) : (
                              <span
                                className="text-xs font-bold text-white truncate"
                                onDoubleClick={(e) => { e.stopPropagation(); setRenamingSetId(set.id); setRenameSetName(set.name); }}
                                title="Double-click to rename"
                              >
                                {set.name}
                              </span>
                            )}
                            {set.isActive && (
                              <span className="shrink-0 text-[8px] font-bold uppercase text-emerald-400 bg-emerald-950/40 border border-emerald-500/30 px-1.5 py-0.5 rounded-sm">Active</span>
                            )}
                          </div>
                          <div className="flex items-center justify-between text-[9px] text-text-warm/40">
                            <span>{set.itemCount ?? 0} image{set.itemCount === 1 ? '' : 's'}</span>
                            <div className="flex items-center gap-2">
                              {!set.isActive && (
                                <button
                                  onClick={(e) => { e.stopPropagation(); handleActivateSet(set.id); }}
                                  className="text-sp-gold hover:text-white font-bold uppercase cursor-pointer"
                                >
                                  Set Active
                                </button>
                              )}
                              <button
                                onClick={(e) => { e.stopPropagation(); handleDeleteSet(set.id); }}
                                className="text-rose-400 hover:text-rose-300 cursor-pointer"
                                title="Delete set"
                              >
                                <Trash2 className="w-3 h-3" />
                              </button>
                            </div>
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>

                {!selectedCarouselSetId ? (
                  <div className="lg:col-span-3 p-8 text-center text-text-warm/40 border border-dashed border-sp-bronze/45 rounded-xl self-start">
                    Create or select a set on the left to manage its screenshots.
                  </div>
                ) : (
                  <>
                    {/* COLUMN 2: CAROUSEL SLIDE FORM */}
                    <div className="lg:col-span-1 space-y-6">
                      <form onSubmit={handleCarouselSubmit} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass text-left">
                        <h3 className="font-serif text-base text-rose-300 uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
                          <ImageIcon className="w-5 h-5 text-sp-gold" />
                          {editingSlideId ? "Modify Slide Specs" : "Forge New Slide"}
                        </h3>

                        <div>
                          <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Slide Title</label>
                          <input
                            type="text"
                            required
                            value={slideTitle}
                            onChange={e => setSlideTitle(e.target.value)}
                            className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                            placeholder="e.g. Mechanical Airship Fleet"
                          />
                        </div>

                        <div>
                          <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Description / Subtitle</label>
                          <textarea
                            required
                            rows={3}
                            value={slideDesc}
                            onChange={e => setSlideDesc(e.target.value)}
                            className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed"
                            placeholder="Describe this scene or update for players..."
                          />
                        </div>

                        <div>
                          <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">
                            Show In These Sets
                          </label>
                          <p className="text-[9px] text-text-warm/40 mb-1.5 leading-relaxed">
                            One upload can appear in several sets — no need to re-upload the same image per set.
                          </p>
                          <div className="flex flex-wrap gap-1.5">
                            {carouselSets.map(set => (
                              <button
                                key={set.id}
                                type="button"
                                onClick={() => setSlideSetIds(prev => prev.includes(set.id) ? prev.filter(s => s !== set.id) : [...prev, set.id])}
                                className={`px-2.5 py-1 rounded text-[10px] font-bold transition-all cursor-pointer border ${
                                  slideSetIds.includes(set.id)
                                    ? 'bg-sp-gold/20 border-sp-gold text-sp-gold'
                                    : 'bg-neutral-950/40 border-sp-bronze/25 text-text-warm/50 hover:text-white'
                                }`}
                              >
                                {set.name}
                              </button>
                            ))}
                          </div>
                        </div>

                        <div>
                          <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">
                            Upload Image (Auto-resizes to 1200x600)
                          </label>
                          <input
                            type="file"
                            id="slide-image-file"
                            accept="image/*"
                            onChange={handleImageFileChange}
                            className="w-full text-xs text-text-warm file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-sp-bronze/20 file:text-sp-gold hover:file:bg-sp-bronze/35 file:cursor-pointer cursor-pointer"
                          />
                          {imageError && (
                            <p className="text-red-400 text-[10px] mt-1 font-bold">{imageError}</p>
                          )}
                        </div>

                        {isProcessingImage && (
                          <div className="text-[10px] text-sp-gold animate-pulse italic">
                            Pressurizing canvas gears & resizing layout...
                          </div>
                        )}

                        {slideImageSrc && !isProcessingImage && (
                          <div className="space-y-1">
                            <span className="block text-[8px] uppercase font-bold text-text-warm/40">Processed Specimen (1200x600)</span>
                            <div className="relative border border-sp-gold/30 rounded-lg overflow-hidden h-[120px] bg-black/50">
                              <img src={slideImageSrc} alt="Resized Preview" className="w-full h-full object-cover" />
                              <div className="absolute top-1.5 right-1.5 bg-black/80 px-1.5 py-0.5 text-[8px] font-bold text-sp-gold uppercase border border-sp-gold/20 rounded-sm">
                                1200 x 600 Ready
                              </div>
                            </div>
                          </div>
                        )}

                        <div className="flex gap-3 pt-2">
                          {editingSlideId && (
                            <button
                              type="button"
                              onClick={() => {
                                setEditingSlideId(null);
                                setSlideTitle('');
                                setSlideDesc('');
                                setSlideImageSrc('');
                                setSlideSetIds(selectedCarouselSetId ? [selectedCarouselSetId] : []);
                                const fileInput = document.getElementById('slide-image-file');
                                if (fileInput) fileInput.value = '';
                              }}
                              className="flex-1 py-2 bg-neutral-900 border border-sp-bronze hover:border-white text-xs font-bold text-white rounded-lg transition-all cursor-pointer font-serif uppercase text-center"
                            >
                              Cancel
                            </button>
                          )}
                          <button
                            type="submit"
                            disabled={isProcessingImage}
                            className="flex-1 py-2.5 bg-sp-gold hover:bg-white border border-transparent rounded-lg text-xs font-bold uppercase tracking-wider text-black transition-all cursor-pointer font-serif flex items-center justify-center gap-1 disabled:opacity-50"
                          >
                            <Plus className="w-4 h-4" />
                            {editingSlideId ? "Re-align Specs" : "Commit Slide"}
                          </button>
                        </div>
                      </form>
                    </div>

                    {/* COLUMN 3: SLIDES LIST FOR THE SELECTED SET */}
                    <div className="lg:col-span-2 space-y-4">
                      <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2 flex items-center gap-2">
                        <ImageIcon className="w-5 h-5 text-sp-copper" /> {carouselSets.find(s => s.id === selectedCarouselSetId)?.name || 'Set'} ({carouselItems.length})
                      </h3>

                      {loadingCarouselItems ? (
                        <div className="p-8 text-center text-text-warm/40 text-xs italic">Loading screenshots...</div>
                      ) : carouselItems.length === 0 ? (
                        <div className="p-8 text-center text-text-warm/40 border border-dashed border-sp-bronze/45 rounded-xl">
                          No screenshots in this set yet. Add one on the left.
                        </div>
                      ) : (
                        <>
                        {carouselItems.length > 1 && (
                          <p className="text-[10px] text-text-warm/40 italic flex items-center gap-1.5"><GripVertical className="w-3 h-3" /> Drag a slide to reorder — this is the order players see on the homepage.</p>
                        )}
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          {carouselItems.map((slide) => (
                            <div
                              key={slide.id}
                              draggable={carouselItems.length > 1}
                              onDragStart={() => setDraggedSlideId(slide.id)}
                              onDragOver={(e) => e.preventDefault()}
                              onDrop={(e) => {
                                // Always prevent the browser's own default
                                // drop behavior (opening/navigating to the
                                // dropped image) — without this a drop that
                                // lands squarely on the <img> itself, which
                                // browsers treat as natively draggable
                                // regardless of the parent's `draggable`
                                // flag, can navigate the whole page to the
                                // image's URL, surfacing as a bare 404 for
                                // any relative path. The actual reorder
                                // logic still only runs with >1 slide.
                                e.preventDefault();
                                if (carouselItems.length > 1) handleSlideDrop(slide.id);
                              }}
                              className={`bg-sp-dark/95 border border-sp-bronze rounded-xl overflow-hidden shadow-md flex flex-col justify-between transition-opacity ${carouselItems.length > 1 ? 'cursor-grab' : ''} ${draggedSlideId === slide.id ? 'opacity-40' : ''}`}
                            >
                              <div className="relative h-[130px] w-full bg-black/20">
                                <img src={slide.src} alt={slide.title} draggable={false} className="w-full h-full object-cover" />
                                <div className="absolute inset-0 bg-linear-to-t from-black via-black/40 to-transparent" />
                                {slide.setCount > 1 && (
                                  <span className="absolute top-2 right-2 text-[8px] px-1.5 py-0.5 rounded uppercase font-mono font-bold bg-black/60 border border-sp-gold/40 text-sp-gold">
                                    In {slide.setCount} sets
                                  </span>
                                )}
                                <div className="absolute bottom-2.5 left-3 right-3 text-left">
                                  <h4 className="font-serif text-sm font-bold text-sp-gold tracking-wide uppercase truncate">
                                    {slide.title}
                                  </h4>
                                  <p className="text-[10px] text-text-warm/95 truncate">
                                    {slide.desc}
                                  </p>
                                </div>
                              </div>

                              <div className="p-3 bg-neutral-950/45 border-t border-sp-bronze/20 flex justify-end gap-2">
                                <button
                                  onClick={() => handleEditClick(slide)}
                                  className="px-2.5 py-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze hover:border-sp-gold text-sp-gold hover:text-black rounded text-[10px] font-bold uppercase transition-all cursor-pointer flex items-center gap-1"
                                >
                                  <Edit className="w-3 h-3" /> Edit
                                </button>
                                <button
                                  onClick={() => handleDeleteClick(slide.id, slide.setCount)}
                                  className="px-2.5 py-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded text-[10px] font-bold uppercase transition-all cursor-pointer flex items-center gap-1"
                                >
                                  <Trash2 className="w-3 h-3" /> Delete
                                </button>
                              </div>
                            </div>
                          ))}
                        </div>
                        </>
                      )}
                    </div>
                  </>
                )}
              </div>
            )}

            {effectiveSettingsSubTab === 'guide' && (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                <div className="lg:col-span-1">
                  <form onSubmit={async (e) => {
                    e.preventDefault();
                    setError(''); setSuccessMsg('');
                    const method = guideEditId ? 'PUT' : 'POST';
                    let res;
                    if (guideAttachmentFile || guideRemoveAttachment) {
                      const formData = new FormData();
                      if (guideEditId) formData.append('id', guideEditId);
                      formData.append('title', guideTitle);
                      formData.append('content', guideContent);
                      formData.append('role', user.role);
                      if (guideAttachmentFile) formData.append('file', guideAttachmentFile);
                      if (guideRemoveAttachment) formData.append('removeAttachment', 'true');
                      res = await fetch('/api/guide', { method, body: formData });
                    } else {
                      res = await fetch('/api/guide', { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id: guideEditId || undefined, title: guideTitle, content: guideContent, role: user.role }) });
                    }
                    const data = await res.json();
                    if (res.ok) {
                      setGuideSteps(data.guide); setGuideTitle(''); setGuideContent(''); setGuideEditId('');
                      setGuideAttachmentFile(null); setGuideExistingAttachment(null); setGuideRemoveAttachment(false);
                      setSuccessMsg('Guide step saved!'); setTimeout(() => setSuccessMsg(''), 3000);
                    } else setError(data.error);
                  }} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass text-left">
                    <h3 className="font-serif text-sm text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2"><Compass className="w-4 h-4" /> {guideEditId ? 'Edit Step' : 'New Guide Step'}</h3>
                    <div><label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Title</label><input required value={guideTitle} onChange={e => setGuideTitle(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold" placeholder="e.g. Read the rules" /></div>
                    <div><label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Content (Markdown)</label><textarea required rows={5} value={guideContent} onChange={e => setGuideContent(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed" placeholder="Description with **bold** and [links](/page)" /></div>
                    <div>
                      <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Attachment (image or PDF, optional)</label>
                      {guideAttachmentFile ? (
                        <div className="flex items-center justify-between gap-2 bg-neutral-900 border border-sp-bronze/40 rounded-lg px-3 py-2">
                          <span className="text-[11px] text-text-warm/70 truncate flex items-center gap-1.5"><Paperclip className="w-3.5 h-3.5 shrink-0" /> {guideAttachmentFile.name}</span>
                          <button type="button" onClick={() => setGuideAttachmentFile(null)} className="text-text-warm/40 hover:text-rose-400 shrink-0"><X className="w-3.5 h-3.5" /></button>
                        </div>
                      ) : guideExistingAttachment && !guideRemoveAttachment ? (
                        <div className="flex items-center justify-between gap-2 bg-neutral-900 border border-sp-bronze/40 rounded-lg px-3 py-2">
                          <a href={guideExistingAttachment.attachmentUrl} target="_blank" rel="noopener noreferrer" className="text-[11px] text-sp-gold hover:text-white truncate flex items-center gap-1.5"><Paperclip className="w-3.5 h-3.5 shrink-0" /> {guideExistingAttachment.attachmentName}</a>
                          <button type="button" onClick={() => setGuideRemoveAttachment(true)} className="text-text-warm/40 hover:text-rose-400 shrink-0" title="Remove attachment"><X className="w-3.5 h-3.5" /></button>
                        </div>
                      ) : (
                        <label className="flex items-center gap-2 w-fit px-3 py-2 bg-neutral-900 border border-sp-bronze/40 hover:border-sp-gold text-text-warm/60 hover:text-sp-gold text-[10px] uppercase font-bold rounded-lg cursor-pointer transition-colors">
                          <Paperclip className="w-3.5 h-3.5" /> Attach File
                          <input type="file" accept="image/*,application/pdf" onChange={e => { const f = e.target.files?.[0]; if (f) { setGuideAttachmentFile(f); setGuideRemoveAttachment(false); } e.target.value = ''; }} className="hidden" />
                        </label>
                      )}
                    </div>
                    <div className="flex gap-2">
                      {guideEditId && <button type="button" onClick={() => { setGuideEditId(''); setGuideTitle(''); setGuideContent(''); setGuideAttachmentFile(null); setGuideExistingAttachment(null); setGuideRemoveAttachment(false); }} className="flex-1 py-2 border border-sp-bronze text-xs uppercase rounded-lg text-text-warm/60 hover:text-white transition-all cursor-pointer">Cancel</button>}
                      <button type="submit" className="flex-1 py-2 bg-sp-gold text-black font-bold uppercase text-xs rounded-lg hover:bg-white transition-all cursor-pointer">{guideEditId ? 'Save' : 'Add Step'}</button>
                    </div>
                  </form>
                </div>
                <div className="lg:col-span-2 space-y-3">
                  <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2 text-left font-bold">Guide Steps ({guideSteps.length})</h3>
                  {guideSteps.map((step, i) => (
                    <div key={step.id} className="bg-sp-dark/80 border border-sp-bronze/40 rounded-xl p-4 flex items-start justify-between gap-4 text-left">
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-bold text-white uppercase">{i + 1}. {step.title}</p>
                        <p className="text-[11px] text-text-warm/50 mt-1 truncate">{step.content}</p>
                        {step.attachmentUrl && (
                          <a href={step.attachmentUrl} target="_blank" rel="noopener noreferrer" className="text-[10px] text-sp-gold hover:text-white flex items-center gap-1 mt-1">
                            <Paperclip className="w-3 h-3" /> {step.attachmentName || 'Attachment'}
                          </a>
                        )}
                      </div>
                      <div className="flex gap-1.5 shrink-0">
                        <button onClick={() => { setGuideEditId(step.id); setGuideTitle(step.title); setGuideContent(step.content); setGuideAttachmentFile(null); setGuideRemoveAttachment(false); setGuideExistingAttachment(step.attachmentUrl ? { attachmentUrl: step.attachmentUrl, attachmentName: step.attachmentName } : null); }} className="p-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded transition-all cursor-pointer"><Edit className="w-3.5 h-3.5" /></button>
                        <button onClick={async () => { if (!confirm('Delete this step?')) return; const res = await fetch(`/api/guide?id=${step.id}&role=${user.role}`, { method: 'DELETE' }); const d = await res.json(); if (d.guide) setGuideSteps(d.guide); }} className="p-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded transition-all cursor-pointer"><Trash2 className="w-3.5 h-3.5" /></button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {effectiveSettingsSubTab === 'rules' && (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                <div className="lg:col-span-1">
                  <form onSubmit={async (e) => {
                    e.preventDefault();
                    setError(''); setSuccessMsg('');
                    const method = ruleEditId ? 'PUT' : 'POST';
                    const res = await fetch('/api/rules', { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id: ruleEditId || undefined, title: ruleTitle, content: ruleContent, role: user.role }) });
                    const data = await res.json();
                    if (res.ok) { setRules(data.rules); setRuleTitle(''); setRuleContent(''); setRuleEditId(''); setSuccessMsg('Rule saved!'); setTimeout(() => setSuccessMsg(''), 3000); }
                    else setError(data.error);
                  }} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass text-left">
                    <h3 className="font-serif text-sm text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2"><Shield className="w-4 h-4" /> {ruleEditId ? 'Edit Rule' : 'New Rule'}</h3>
                    <div><label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Rule Title</label><input required value={ruleTitle} onChange={e => setRuleTitle(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold" placeholder="e.g. No Hacking" /></div>
                    <div><label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Rule Content (Markdown)</label><textarea required rows={6} value={ruleContent} onChange={e => setRuleContent(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed" placeholder="Rule description with **bold** text..." /></div>
                    <div className="flex gap-2">
                      {ruleEditId && <button type="button" onClick={() => { setRuleEditId(''); setRuleTitle(''); setRuleContent(''); }} className="flex-1 py-2 border border-sp-bronze text-xs uppercase rounded-lg text-text-warm/60 hover:text-white transition-all cursor-pointer">Cancel</button>}
                      <button type="submit" className="flex-1 py-2 bg-sp-gold text-black font-bold uppercase text-xs rounded-lg hover:bg-white transition-all cursor-pointer">{ruleEditId ? 'Save' : 'Add Rule'}</button>
                    </div>
                  </form>
                </div>
                <div className="lg:col-span-2 space-y-3">
                  <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2 text-left font-bold">Active Rules ({rules.length})</h3>
                  {rules.map((rule, i) => (
                    <div key={rule.id} className="bg-sp-dark/80 border border-sp-bronze/40 rounded-xl p-4 flex items-start justify-between gap-4 text-left">
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-bold text-white uppercase">§{i + 1} — {rule.title}</p>
                        <p className="text-[11px] text-text-warm/50 mt-1 line-clamp-2">{rule.content}</p>
                      </div>
                      <div className="flex gap-1.5 shrink-0">
                        <button onClick={() => { setRuleEditId(rule.id); setRuleTitle(rule.title); setRuleContent(rule.content); }} className="p-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded transition-all cursor-pointer"><Edit className="w-3.5 h-3.5" /></button>
                        <button onClick={async () => { if (!confirm('Delete this rule?')) return; const res = await fetch(`/api/rules?id=${rule.id}&role=${user.role}`, { method: 'DELETE' }); const d = await res.json(); if (d.rules) setRules(d.rules); }} className="p-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded transition-all cursor-pointer"><Trash2 className="w-3.5 h-3.5" /></button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {effectiveSettingsSubTab === 'tips' && (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                <div className="lg:col-span-1">
                  <form onSubmit={async (e) => {
                    e.preventDefault();
                    setError(''); setSuccessMsg('');
                    const method = tipEditId ? 'PUT' : 'POST';
                    const res = await fetch('/api/tips', { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id: tipEditId || undefined, content: tipContent, active: tipActive, role: user.role }) });
                    const data = await res.json();
                    if (res.ok) { setTips(data.tips); setTipContent(''); setTipEditId(''); setTipActive(true); setSuccessMsg('Tip saved!'); setTimeout(() => setSuccessMsg(''), 3000); }
                    else setError(data.error);
                  }} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass text-left">
                    <h3 className="font-serif text-sm text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2"><Lightbulb className="w-4 h-4" /> {tipEditId ? 'Edit Tip' : 'New Tip'}</h3>
                    <p className="text-[10px] text-text-warm/40">One active tip is shown on the homepage per day (rotates automatically), picked from every tip marked active below.</p>
                    <div><label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Tip Text</label><textarea required rows={4} value={tipContent} onChange={e => setTipContent(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed" placeholder="e.g. Vote for Iekcorbar every 24 hours..." /></div>
                    <label className="flex items-center gap-2 text-[10px] uppercase font-bold text-text-warm/60 cursor-pointer">
                      <input type="checkbox" checked={tipActive} onChange={e => setTipActive(e.target.checked)} className="cursor-pointer" /> Active (eligible to show)
                    </label>
                    <div className="flex gap-2">
                      {tipEditId && <button type="button" onClick={() => { setTipEditId(''); setTipContent(''); setTipActive(true); }} className="flex-1 py-2 border border-sp-bronze text-xs uppercase rounded-lg text-text-warm/60 hover:text-white transition-all cursor-pointer">Cancel</button>}
                      <button type="submit" className="flex-1 py-2 bg-sp-gold text-black font-bold uppercase text-xs rounded-lg hover:bg-white transition-all cursor-pointer">{tipEditId ? 'Save' : 'Add Tip'}</button>
                    </div>
                  </form>
                </div>
                <div className="lg:col-span-2 space-y-3">
                  <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2 text-left font-bold">All Tips ({tips.length})</h3>
                  {tips.map((tip) => (
                    <div key={tip.id} className={`bg-sp-dark/80 border rounded-xl p-4 flex items-start justify-between gap-4 text-left ${tip.active ? 'border-sp-bronze/40' : 'border-sp-bronze/15 opacity-50'}`}>
                      <div className="flex-1 min-w-0">
                        {!tip.active && <span className="text-[9px] uppercase font-bold text-text-warm/40">Inactive — </span>}
                        <p className="text-xs text-text-warm/80 inline">{tip.content}</p>
                      </div>
                      <div className="flex gap-1.5 shrink-0">
                        <button onClick={() => { setTipEditId(tip.id); setTipContent(tip.content); setTipActive(tip.active); }} className="p-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded transition-all cursor-pointer"><Edit className="w-3.5 h-3.5" /></button>
                        <button onClick={async () => { if (!confirm('Delete this tip?')) return; const res = await fetch(`/api/tips?id=${tip.id}&role=${user.role}`, { method: 'DELETE' }); const d = await res.json(); if (d.tips) setTips(d.tips); }} className="p-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded transition-all cursor-pointer"><Trash2 className="w-3.5 h-3.5" /></button>
                      </div>
                    </div>
                  ))}
                  {tips.length === 0 && (
                    <div className="text-center py-8 text-text-warm/30 border border-dashed border-sp-bronze/30 rounded-xl text-xs">No tips yet.</div>
                  )}
                </div>
              </div>
            )}

            {effectiveSettingsSubTab === 'vote' && (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                <div className="lg:col-span-1">
                  <form onSubmit={async (e) => {
                    e.preventDefault();
                    setError(''); setSuccessMsg('');
                    const method = voteEditId ? 'PUT' : 'POST';
                    const res = await fetch('/api/vote/sites', { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id: voteEditId || undefined, name: voteName, url: voteUrl, icon: voteIcon, role: user.role }) });
                    const data = await res.json();
                    if (res.ok) { setVoteSites(data.sites); setVoteName(''); setVoteUrl(''); setVoteIcon('🗳️'); setVoteEditId(''); setSuccessMsg('Vote site saved!'); setTimeout(() => setSuccessMsg(''), 3000); }
                    else setError(data.error);
                  }} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass text-left">
                    <h3 className="font-serif text-sm text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2"><ThumbsUp className="w-4 h-4" /> {voteEditId ? 'Edit Vote Site' : 'Add Vote Site'}</h3>
                    <div><label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Icon Emoji</label><input value={voteIcon} onChange={e => setVoteIcon(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold" placeholder="🗳️" /></div>
                    <div><label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Site Name</label><input required value={voteName} onChange={e => setVoteName(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold" placeholder="e.g. Planet Minecraft" /></div>
                    <div><label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Vote URL</label><input required type="url" value={voteUrl} onChange={e => setVoteUrl(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold" placeholder="https://..." /></div>
                    <div className="flex gap-2">
                      {voteEditId && <button type="button" onClick={() => { setVoteEditId(''); setVoteName(''); setVoteUrl(''); setVoteIcon('🗳️'); }} className="flex-1 py-2 border border-sp-bronze text-xs uppercase rounded-lg text-text-warm/60 hover:text-white transition-all cursor-pointer">Cancel</button>}
                      <button type="submit" className="flex-1 py-2 bg-sp-gold text-black font-bold uppercase text-xs rounded-lg hover:bg-white transition-all cursor-pointer">{voteEditId ? 'Save' : 'Add Site'}</button>
                    </div>
                  </form>
                </div>
                <div className="lg:col-span-2 space-y-3">
                  <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2 text-left font-bold">Vote Sites ({voteSites.length})</h3>
                  {voteSites.map(site => (
                    <div key={site.id} className="bg-sp-dark/80 border border-sp-bronze/40 rounded-xl p-4 flex items-center justify-between gap-4 text-left">
                      <div className="flex items-center gap-3 min-w-0"><span className="text-xl">{site.icon || '🗳️'}</span><div className="min-w-0"><p className="text-xs font-bold text-white">{site.name}</p><p className="text-[10px] text-text-warm/40 font-mono truncate">{site.url}</p></div></div>
                      <div className="flex gap-1.5 shrink-0">
                        <button onClick={() => { setVoteEditId(site.id); setVoteName(site.name); setVoteUrl(site.url); setVoteIcon(site.icon || '🗳️'); }} className="p-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded transition-all cursor-pointer"><Edit className="w-3.5 h-3.5" /></button>
                        <button onClick={async () => {
                          if (!confirm('Delete this vote site?')) return;
                          setError(''); setSuccessMsg('');
                          const res = await fetch(`/api/vote/sites?id=${site.id}&role=${user.role}`, { method: 'DELETE' });
                          const d = await res.json();
                          if (res.ok && d.sites) { setVoteSites(d.sites); setSuccessMsg('Vote site deleted!'); setTimeout(() => setSuccessMsg(''), 3000); }
                          else setError(d.error || 'Failed to delete vote site');
                        }} className="p-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded transition-all cursor-pointer"><Trash2 className="w-3.5 h-3.5" /></button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {effectiveSettingsSubTab === 'projects' && (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                <div className="lg:col-span-1">
                  <form onSubmit={async (e) => {
                    e.preventDefault();
                    setError(''); setSuccessMsg('');
                    const res = await fetch('/api/projects', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({
                        id: projectEditId || undefined,
                        title: projectTitle,
                        category: projectCategory,
                        description: projectDescription,
                        status: projectStatus,
                        maxParticipants: projectMaxParticipants ? parseInt(projectMaxParticipants) : null,
                        createdBy: user.mcUsername,
                        role: user.role
                      })
                    });
                    const data = await res.json();
                    if (res.ok) {
                      fetchProjects();
                      setProjectTitle('');
                      setProjectCategory('Building');
                      setProjectDescription('');
                      setProjectStatus('open');
                      setProjectMaxParticipants('');
                      setProjectEditId('');
                      setSuccessMsg('Blueprint saved!');
                      setTimeout(() => setSuccessMsg(''), 3000);
                    } else {
                      setError(data.error || 'Failed to save blueprint');
                    }
                  }} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass text-left">
                    <h3 className="font-serif text-sm text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2 flex items-center gap-2">
                      <Hammer className="w-4 h-4" /> {projectEditId ? 'Edit Blueprint' : 'New Blueprint'}
                    </h3>
                    <div>
                      <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Project Title</label>
                      <input required value={projectTitle} onChange={e => setProjectTitle(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold" placeholder="e.g. Grand Clocktower Restoration" />
                    </div>
                    <div>
                      <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Category</label>
                      <select value={projectCategory} onChange={e => setProjectCategory(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold cursor-pointer">
                        <option value="Building">Building</option>
                        <option value="Infrastructure">Infrastructure</option>
                        <option value="Redstone">Redstone</option>
                        <option value="Economy">Economy</option>
                        <option value="General">General</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Status</label>
                      <select value={projectStatus} onChange={e => setProjectStatus(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold cursor-pointer">
                        <option value="open">Open</option>
                        <option value="in-progress">In Progress</option>
                        <option value="completed">Completed</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Max Participants (Optional)</label>
                      <input type="number" value={projectMaxParticipants} onChange={e => setProjectMaxParticipants(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold" placeholder="e.g. 12" />
                    </div>
                    <div>
                      <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">Description (Markdown)</label>
                      <textarea required rows={6} value={projectDescription} onChange={e => setProjectDescription(e.target.value)} className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold leading-relaxed" placeholder="Objective, materials, rewards..." />
                    </div>
                    <div className="flex gap-2">
                      {projectEditId && <button type="button" onClick={() => { setProjectEditId(''); setProjectTitle(''); setProjectCategory('Building'); setProjectDescription(''); setProjectStatus('open'); setProjectMaxParticipants(''); }} className="flex-1 py-2 border border-sp-bronze text-xs uppercase rounded-lg text-text-warm/60 hover:text-white transition-all cursor-pointer">Cancel</button>}
                      <button type="submit" className="flex-1 py-2 bg-sp-gold text-black font-bold uppercase text-xs rounded-lg hover:bg-white transition-all cursor-pointer">{projectEditId ? 'Save' : 'Add Project'}</button>
                    </div>
                  </form>
                </div>
                <div className="lg:col-span-2 space-y-3">
                  <h3 className="font-serif text-lg text-sp-gold uppercase tracking-wide border-b border-sp-bronze/25 pb-2 text-left font-bold">Active Projects ({projects.length})</h3>
                  {projects.length === 0 ? (
                    <div className="text-center py-16 text-text-warm/40 border border-dashed border-sp-bronze/30 rounded-xl">
                      No community projects have been created yet.
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {projects.map(proj => (
                        <div key={proj.id} className="bg-sp-dark/80 border border-sp-bronze/40 rounded-xl p-4 flex items-start justify-between gap-4 text-left">
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2 mb-1">
                              <span className={`text-[8px] font-bold uppercase px-1.5 py-0.5 rounded border ${
                                proj.status === 'completed'
                                  ? 'bg-blue-500/10 border-blue-500/30 text-blue-400'
                                  : proj.status === 'in-progress'
                                    ? 'bg-amber-500/10 border-amber-500/30 text-amber-400'
                                    : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                              }`}>
                                {proj.status}
                              </span>
                              <span className="text-[8px] font-bold uppercase px-1.5 py-0.5 bg-sp-copper/10 border border-sp-copper/30 text-sp-copper rounded">
                                {proj.category}
                              </span>
                              {proj.maxParticipants && (
                                <span className="text-[8px] font-bold uppercase px-1.5 py-0.5 bg-neutral-900 border border-sp-bronze/20 text-text-warm/40 rounded">
                                  Max: {proj.maxParticipants}
                                </span>
                              )}
                            </div>
                            <p className="text-xs font-bold text-white uppercase">{proj.title}</p>
                            <p className="text-[10px] text-text-warm/50 mt-1 line-clamp-2 leading-relaxed">{proj.description}</p>
                          </div>
                          <div className="flex gap-1.5 shrink-0">
                            <button onClick={() => { setProjectEditId(proj.id); setProjectTitle(proj.title); setProjectCategory(proj.category); setProjectDescription(proj.description); setProjectStatus(proj.status); setProjectMaxParticipants(proj.maxParticipants || ''); }} className="p-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded transition-all cursor-pointer"><Edit className="w-3.5 h-3.5" /></button>
                            <button onClick={async () => { if (!confirm('Delete this community project?')) return; const res = await fetch(`/api/projects?id=${proj.id}&role=${user.role}`, { method: 'DELETE' }); if (res.ok) fetchProjects(); }} className="p-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded transition-all cursor-pointer"><Trash2 className="w-3.5 h-3.5" /></button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}

            {effectiveSettingsSubTab === 'content' && siteContentDraft && (
              <div className="max-w-2xl text-left space-y-6">
                <p className="text-xs text-text-warm/60">
                  Rename how roles and a couple of page titles are displayed across the site — e.g. to match your actual LuckPerms group names. This doesn&apos;t change permissions, only the text shown.
                </p>

                <form
                  onSubmit={async (e) => {
                    e.preventDefault();
                    setError(''); setSuccessMsg('');
                    setSiteContentSaving(true);
                    try {
                      const res = await fetch('/api/site-content', {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ entries: siteContentDraft })
                      });
                      const data = await res.json();
                      if (res.ok && data.success) {
                        setSuccessMsg('Site content saved! Other tabs/pages pick it up on next load.');
                        setTimeout(() => setSuccessMsg(''), 4000);
                      } else {
                        setError(data.error || 'Failed to save site content');
                      }
                    } catch {
                      setError('Network error');
                    } finally {
                      setSiteContentSaving(false);
                    }
                  }}
                  className="space-y-6"
                >
                  {Object.entries(
                    SITE_CONTENT_DEFAULTS.reduce((groups, item) => {
                      (groups[item.category] = groups[item.category] || []).push(item);
                      return groups;
                    }, {})
                  ).map(([category, items]) => (
                    <div key={category} className="bg-sp-dark border border-sp-bronze p-5 rounded-xl space-y-4 steampunk-glass">
                      <h3 className="font-serif text-sm text-sp-gold uppercase tracking-wide border-b border-sp-bronze/20 pb-2">
                        {SITE_CONTENT_CATEGORY_LABELS[category] || category}
                      </h3>
                      {items.map(item => (
                        <div key={item.key}>
                          <label className="block text-[9px] uppercase font-bold text-sp-gold/80 mb-1">{item.label}</label>
                          <input
                            value={siteContentDraft[item.key] ?? item.value}
                            onChange={e => setSiteContentDraft(prev => ({ ...prev, [item.key]: e.target.value }))}
                            className="w-full bg-neutral-900 border border-sp-bronze text-white text-xs rounded-lg px-3 py-2.5 focus:outline-hidden focus:border-sp-gold"
                            placeholder={item.value}
                          />
                        </div>
                      ))}
                    </div>
                  ))}

                  <button
                    type="submit"
                    disabled={siteContentSaving}
                    className="px-5 py-2.5 bg-sp-gold hover:bg-white disabled:opacity-50 text-black font-bold uppercase text-xs rounded-lg transition-all cursor-pointer"
                  >
                    {siteContentSaving ? 'Saving...' : 'Save Site Content'}
                  </button>
                </form>
              </div>
            )}

            {effectiveSettingsSubTab === 'cleanup' && (
              <div className="max-w-4xl text-left space-y-4">
                <div className="flex items-start justify-between gap-4 flex-wrap">
                  <p className="text-xs text-text-warm/60 max-w-xl">
                    Accounts with no in-game activity for {inactivityDays}+ days. Purging an account permanently deletes it, removes the player from the server whitelist, and clears their private data (journal, friendships, notifications, sessions). They can register again from scratch if they return. Staff (admin/moderator) accounts are never shown here.
                  </p>
                  <button
                    onClick={fetchInactiveCandidates}
                    disabled={loadingInactive}
                    className="px-3 py-1.5 bg-neutral-900 border border-sp-bronze hover:border-sp-gold text-white text-[10px] font-bold uppercase rounded-lg transition-all cursor-pointer flex items-center gap-1.5 disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${loadingInactive ? 'animate-spin' : ''}`} /> Refresh
                  </button>
                </div>

                {inactiveLoadError && (
                  <div className="bg-crimson/10 border border-crimson/40 text-rose-300 text-xs p-3 rounded-lg">{inactiveLoadError}</div>
                )}

                {purgeResults && (
                  <div className="bg-sp-dark border border-sp-bronze rounded-lg p-3 space-y-1 text-xs">
                    <p className="text-sp-gold font-bold uppercase text-[10px] mb-1">Purge Results</p>
                    {purgeResults.map((r, i) => (
                      <p key={i} className={r.success ? 'text-green-400' : 'text-rose-300'}>
                        {r.success ? '✓' : '✗'} {r.mcUsername || r.email} — {r.success ? `purged & removed from whitelist${r.rconBypassed ? ' (RCON bypassed — dev env)' : ''}` : r.error}
                      </p>
                    ))}
                  </div>
                )}

                {loadingInactive ? (
                  <p className="text-xs text-text-warm/50 italic">Scanning for inactive accounts...</p>
                ) : inactiveCandidates.length === 0 ? (
                  <p className="text-xs text-text-warm/50 italic">No accounts have been inactive for {inactivityDays}+ days.</p>
                ) : (
                  <div className="bg-sp-dark border border-sp-bronze rounded-xl steampunk-glass overflow-hidden">
                    <div className="overflow-x-auto">
                      <table className="w-full text-xs">
                        <thead>
                          <tr className="border-b border-sp-bronze/30 text-text-warm/50 uppercase text-[9px]">
                            <th className="p-3 text-left">
                              <input type="checkbox" checked={selectedInactiveEmails.length === inactiveCandidates.length} onChange={toggleSelectAllInactive} className="cursor-pointer" />
                            </th>
                            <th className="p-3 text-left">IGN</th>
                            <th className="p-3 text-left">Email</th>
                            <th className="p-3 text-left">Last Active</th>
                            <th className="p-3 text-left">Registered</th>
                          </tr>
                        </thead>
                        <tbody>
                          {inactiveCandidates.map(c => (
                            <tr key={c.email} className="border-b border-sp-bronze/10 hover:bg-black/20">
                              <td className="p-3">
                                <input type="checkbox" checked={selectedInactiveEmails.includes(c.email)} onChange={() => toggleInactiveSelection(c.email)} className="cursor-pointer" />
                              </td>
                              <td className="p-3 text-white font-bold">{c.mcUsername || '—'}</td>
                              <td className="p-3 text-text-warm/70">{c.email}</td>
                              <td className="p-3 text-text-warm/70">{c.lastActive ? new Date(c.lastActive).toLocaleDateString() : 'Never played'}</td>
                              <td className="p-3 text-text-warm/40">{c.createdAt ? new Date(c.createdAt).toLocaleDateString() : '—'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    <div className="p-3 border-t border-sp-bronze/20 flex items-center justify-between">
                      <span className="text-[10px] text-text-warm/50">{selectedInactiveEmails.length} selected</span>
                      <button
                        onClick={handlePurgeInactive}
                        disabled={selectedInactiveEmails.length === 0 || purgingInactive}
                        className="px-4 py-2 bg-crimson/80 hover:bg-crimson border border-crimson text-white text-[10px] font-bold uppercase tracking-wider rounded-lg transition-all cursor-pointer disabled:opacity-40 flex items-center gap-1.5"
                      >
                        <Trash2 className="w-3.5 h-3.5" /> {purgingInactive ? 'Purging...' : `Purge Selected (${selectedInactiveEmails.length})`}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {isAdmin && effectiveSettingsSubTab === 'audit' && <AuditLogView />}

            {isAdmin && effectiveSettingsSubTab === 'roles' && (
              <div className="max-w-4xl text-left space-y-6">
                <p className="text-xs text-text-warm/60 max-w-2xl">
                  'Admin' always has every tab and can't be edited here — that guarantee is what stops a misconfigured or emptied role list from ever locking every admin out at once. Every other role, including the built-in 'Moderator', can have its Staff Panel tabs toggled below, or you can create a brand new role from scratch (e.g. a "Helper" who only sees Reports and Bug Reports).
                </p>

                {roleError && <div className="bg-crimson/10 border border-crimson/40 text-rose-300 text-xs p-3 rounded-lg">{roleError}</div>}

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  {/* Existing roles */}
                  <div className="space-y-3">
                    <h4 className="text-[10px] uppercase font-bold text-sp-gold/70">Roles</h4>

                    <div className="bg-neutral-900/40 border border-sp-bronze/25 rounded-lg p-3 flex items-center justify-between opacity-70">
                      <span className="text-xs font-bold text-white">Admin</span>
                      <span className="text-[9px] text-text-warm/40 italic">Built-in — every tab, always</span>
                    </div>

                    {loadingRoles ? (
                      <p className="text-xs text-text-warm/40 italic">Loading roles...</p>
                    ) : (
                      [
                        roles.find(r => r.name === 'moderator') || { name: 'moderator', label: 'Moderator', tabs: DEFAULT_MODERATOR_TABS, isVirtual: true },
                        ...roles.filter(r => r.name !== 'moderator')
                          .sort((a, b) => compareRoles(a.name, b.name, roles, n => roles.find(r => r.name === n)?.label || n))
                      ].map(role => (
                        <div key={role.name} className={`bg-neutral-900/40 border rounded-lg p-3 space-y-2 ${roleFormEditing === role.name ? 'border-sp-gold' : 'border-sp-bronze/25'}`}>
                          <div className="flex items-center justify-between gap-2">
                            <div className="min-w-0">
                              <span className={`inline-block text-[10px] px-2 py-0.5 rounded-md font-bold uppercase tracking-wider ${roleBadgeClass(role.name, role.color)}`}>{role.label}</span>
                              <span className="block text-[9px] text-text-warm/40 font-mono truncate mt-1">{role.name}{role.isVirtual ? ' — default, not yet saved' : ''}</span>
                            </div>
                            <div className="flex items-center gap-1.5 shrink-0">
                              <button type="button" onClick={() => openEditRoleForm(role)} className="p-1 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded cursor-pointer" title="Edit">
                                <Edit className="w-3.5 h-3.5" />
                              </button>
                              {!role.isVirtual && (
                                <button type="button" onClick={() => setRoleDeleteConfirm(role.name)} className="p-1 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded cursor-pointer" title="Delete">
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              )}
                            </div>
                          </div>
                          <div className="flex flex-wrap gap-1">
                            {role.tabs.length === 0 ? (
                              <span className="text-[9px] text-text-warm/30 italic">No tabs granted</span>
                            ) : (
                              STAFF_TABS.filter(t => role.tabs.includes(t.key)).map(t => (
                                <span key={t.key} className="text-[9px] bg-sp-gold/10 border border-sp-gold/25 text-sp-gold px-1.5 py-0.5 rounded">{t.label}</span>
                              ))
                            )}
                          </div>
                        </div>
                      ))
                    )}
                  </div>

                  {/* Create/edit form */}
                  <form onSubmit={handleRoleSave} className="bg-sp-dark border border-sp-bronze p-4 rounded-xl space-y-3 steampunk-glass h-fit">
                    <h4 className="text-[10px] uppercase font-bold text-sp-gold/70">{roleFormEditing ? `Edit "${roleFormEditing}"` : 'New Role'}</h4>

                    <div>
                      <label className="block text-[9px] uppercase font-bold text-text-warm/40 mb-1">Role name (stored as the account's role value)</label>
                      <input
                        type="text" value={roleFormName}
                        onChange={e => setRoleFormName(e.target.value)}
                        disabled={!!roleFormEditing}
                        placeholder="helper"
                        required
                        className="w-full bg-neutral-900 border border-sp-bronze/30 text-white text-xs rounded px-3 py-2 focus:outline-hidden focus:border-sp-gold disabled:opacity-50"
                      />
                    </div>

                    <div>
                      <label className="block text-[9px] uppercase font-bold text-text-warm/40 mb-1">Display label</label>
                      <input
                        type="text" value={roleFormLabel}
                        onChange={e => setRoleFormLabel(e.target.value)}
                        placeholder="Helper"
                        required
                        className="w-full bg-neutral-900 border border-sp-bronze/30 text-white text-xs rounded px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                      />
                    </div>

                    <div>
                      <label className="block text-[9px] uppercase font-bold text-text-warm/40 mb-1">Badge colour</label>
                      <div className="flex flex-wrap gap-1.5">
                        {Object.entries(ROLE_COLORS).map(([key, c]) => (
                          <button
                            key={key}
                            type="button"
                            onClick={() => setRoleFormColor(key)}
                            title={c.label}
                            aria-label={c.label}
                            aria-pressed={roleFormColor === key}
                            className={`w-6 h-6 rounded-full ${c.dot} cursor-pointer transition-all ${roleFormColor === key ? 'ring-2 ring-white ring-offset-2 ring-offset-sp-dark scale-110' : 'opacity-60 hover:opacity-100'}`}
                          />
                        ))}
                      </div>
                      <div className="mt-2 text-[9px] text-text-warm/40 flex items-center gap-2">
                        Preview:
                        <span className={`text-[9px] px-2 py-0.5 rounded-md font-bold uppercase tracking-wider ${ROLE_COLORS[roleFormColor]?.badge}`}>{roleFormLabel || 'Helper'}</span>
                      </div>
                    </div>

                    <div className="space-y-2.5">
                      <div className="flex items-center justify-between gap-2">
                        <label className="block text-[9px] uppercase font-bold text-text-warm/40">Staff Panel Permissions</label>
                        <div className="relative">
                          <Search className="w-3 h-3 text-text-warm/40 absolute left-2 top-1.5" />
                          <input
                            type="text"
                            value={roleTabSearch}
                            onChange={e => setRoleTabSearch(e.target.value)}
                            placeholder="Filter..."
                            className="bg-neutral-900 border border-sp-bronze/30 text-white text-[10px] rounded pl-6 pr-2 py-1 w-28 focus:outline-hidden focus:border-sp-gold"
                          />
                        </div>
                      </div>

                      {/* Grouped, with a one-line description per permission —
                          previously this was a bare 2-column checkbox grid of
                          short labels with zero explanation of what each one
                          actually let a role do, which is exactly what made
                          building a custom role a guessing game. 'settings'
                          itself is deliberately excluded: it's an umbrella
                          that gets granted automatically the moment a role
                          has any one Settings Tool below (see
                          staff-tabs.js/permissions.js), not something to
                          tick directly. */}
                      {['Community', 'Content', 'Administration', 'Settings Tools'].map(groupName => {
                        const groupTabs = STAFF_TABS.filter(t => t.group === groupName && !t.isUmbrella);
                        const q = roleTabSearch.trim().toLowerCase();
                        const visibleTabs = q
                          ? groupTabs.filter(t => t.label.toLowerCase().includes(q) || t.description.toLowerCase().includes(q))
                          : groupTabs;
                        if (visibleTabs.length === 0) return null;
                        return (
                          <div key={groupName} className="border border-sp-bronze/20 rounded-lg p-2.5 space-y-1.5 bg-neutral-950/20">
                            <span className="block text-[9px] uppercase font-bold text-sp-copper tracking-wider">
                              {groupName}
                              {groupName === 'Settings Tools' && <span className="text-text-warm/30 font-normal normal-case tracking-normal"> — checking any of these also grants the "Settings" tab itself</span>}
                            </span>
                            {visibleTabs.map(t => (
                              <label key={t.key} className="flex items-start gap-2 text-[11px] text-text-warm/80 cursor-pointer py-0.5">
                                <input type="checkbox" className="mt-0.5 shrink-0" checked={roleFormTabs.includes(t.key)} onChange={() => toggleRoleFormTab(t.key)} />
                                <span>
                                  <span className="block font-bold text-white">{t.label}</span>
                                  <span className="block text-[10px] text-text-warm/50 leading-snug">{t.description}</span>
                                </span>
                              </label>
                            ))}
                          </div>
                        );
                      })}
                      {roleTabSearch.trim() && STAFF_TABS.filter(t => !t.isUmbrella && (t.label.toLowerCase().includes(roleTabSearch.trim().toLowerCase()) || t.description.toLowerCase().includes(roleTabSearch.trim().toLowerCase()))).length === 0 && (
                        <p className="text-[10px] text-text-warm/30 italic text-center">No permissions match "{roleTabSearch}".</p>
                      )}
                    </div>

                    <div className="flex gap-2 pt-1">
                      <button type="submit" disabled={roleSaving} className="px-4 py-2 bg-sp-gold hover:bg-white disabled:opacity-50 text-black text-[10px] font-bold uppercase tracking-wider rounded-lg transition-all cursor-pointer">
                        {roleSaving ? 'Saving...' : (roleFormEditing ? 'Save Role' : 'Create Role')}
                      </button>
                      {roleFormEditing && (
                        <button type="button" onClick={openNewRoleForm} className="px-4 py-2 border border-sp-bronze/40 hover:border-white/40 text-text-warm/60 hover:text-white text-[10px] font-bold uppercase tracking-wider rounded-lg transition-all cursor-pointer">
                          Cancel
                        </button>
                      )}
                    </div>
                  </form>
                </div>

                {roleDeleteConfirm && (
                  <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
                    <div className="bg-neutral-950 border border-crimson/50 rounded-xl p-6 w-full max-w-sm space-y-4 text-center">
                      <p className="text-sm text-white">Delete role <span className="font-bold text-rose-300">"{roleDeleteConfirm}"</span>?</p>
                      <p className="text-[10px] text-text-warm/50">
                        Any user still assigned this role loses every one of its tabs immediately
                        {roleDeleteConfirm === 'moderator' ? ' (falls back to the default Moderator tab set instead of nothing).' : '.'}
                      </p>
                      <div className="flex gap-2">
                        <button onClick={() => setRoleDeleteConfirm(null)} className="flex-1 py-2 border border-sp-bronze/40 text-text-warm/60 hover:text-white text-xs font-bold uppercase rounded-lg cursor-pointer">Cancel</button>
                        <button onClick={confirmDeleteRole} className="flex-1 py-2 bg-crimson hover:bg-red-600 text-white text-xs font-bold uppercase rounded-lg cursor-pointer">Delete</button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}

            {isAdmin && effectiveSettingsSubTab === 'newsletter' && (
              <div className="max-w-2xl text-left space-y-6">
                <p className="text-xs text-text-warm/60 max-w-2xl">
                  Sends an email to every registered player who hasn't unsubscribed (see <code className="text-sp-gold/80">users.newsletter_opt_out</code>) — each one gets their own personalized unsubscribe link. This goes out immediately and can't be recalled once sent.
                </p>

                {newsletterError && <div className="bg-crimson/10 border border-crimson/40 text-rose-300 text-xs p-3 rounded-lg">{newsletterError}</div>}
                {newsletterResult && (
                  <div className="bg-green-500/10 border border-green-500/40 text-green-300 text-xs p-3 rounded-lg">
                    Sent to {newsletterResult.sentCount} recipient{newsletterResult.sentCount === 1 ? '' : 's'}
                    {newsletterResult.bypassCount > 0 && ` (${newsletterResult.bypassCount} bypassed — SMTP not configured)`}
                    {newsletterResult.failedCount > 0 && `, ${newsletterResult.failedCount} failed`}.
                  </div>
                )}

                <div className="space-y-3 bg-neutral-900/40 border border-sp-bronze/25 rounded-lg p-4">
                  <div>
                    <label className="text-[10px] text-text-warm/40 uppercase">Subject</label>
                    <input
                      className="w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                      value={newsletterSubject}
                      onChange={e => setNewsletterSubject(e.target.value)}
                      placeholder="e.g. New Update: Steampunk Brewery Overhaul"
                    />
                  </div>
                  <div>
                    <label className="text-[10px] text-text-warm/40 uppercase mb-1 block">Message</label>
                    <RichTextEditor value={newsletterBody} onChange={setNewsletterBody} placeholder="Write your announcement..." />
                  </div>

                  {!newsletterConfirming ? (
                    <button
                      onClick={() => setNewsletterConfirming(true)}
                      disabled={!newsletterSubject.trim() || !newsletterBody.replace(/<[^>]+>/g, '').trim()}
                      className="px-4 py-2 bg-sp-gold hover:bg-white disabled:opacity-40 text-black text-[10px] font-bold uppercase tracking-wider rounded-lg transition-all cursor-pointer"
                    >
                      Review & Send
                    </button>
                  ) : (
                    <div className="bg-crimson/10 border border-crimson/40 rounded-lg p-3 space-y-2">
                      <p className="text-[11px] text-rose-200">Send this to every subscribed player now? This can't be undone.</p>
                      <div className="flex gap-2">
                        <button onClick={() => setNewsletterConfirming(false)} className="flex-1 py-2 border border-sp-bronze/40 text-text-warm/60 hover:text-white text-[10px] font-bold uppercase rounded-lg cursor-pointer">Cancel</button>
                        <button onClick={sendNewsletter} disabled={sendingNewsletter} className="flex-1 py-2 bg-crimson hover:bg-red-600 disabled:opacity-50 text-white text-[10px] font-bold uppercase rounded-lg cursor-pointer">
                          {sendingNewsletter ? 'Sending...' : 'Confirm Send'}
                        </button>
                      </div>
                    </div>
                  )}
                </div>

                <div className="space-y-2">
                  <h4 className="text-[10px] uppercase font-bold text-sp-gold/70">Send History</h4>
                  {loadingNewsletterHistory ? (
                    <p className="text-xs text-text-warm/40 italic">Loading...</p>
                  ) : newsletterHistory.length === 0 ? (
                    <p className="text-xs text-text-warm/40 italic">No newsletters sent yet.</p>
                  ) : (
                    <div className="space-y-2">
                      {newsletterHistory.map(n => (
                        <div key={n.id} className="bg-neutral-900/30 border border-sp-bronze/20 rounded-lg p-3">
                          <div className="flex items-center justify-between gap-2">
                            <span className="text-xs font-bold text-white">{n.subject}</span>
                            <span className="text-[9px] text-text-warm/40 shrink-0">{new Date(n.sentAt).toLocaleString()}</span>
                          </div>
                          <p className="text-[10px] text-text-warm/50 mt-1">{n.recipientCount} recipient{n.recipientCount === 1 ? '' : 's'} · sent by {n.sentBy}</p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}

            {isAdmin && effectiveSettingsSubTab === 'files' && (
              <div className="max-w-2xl text-left space-y-6">
                <p className="text-xs text-text-warm/60 max-w-2xl">
                  Share plugins, configs, or any other file directly with other admins. Nobody outside the admin role can ever see or download these — not even other staff roles.
                </p>

                {adminFileError && <div className="bg-crimson/10 border border-crimson/40 text-rose-300 text-xs p-3 rounded-lg">{adminFileError}</div>}

                <form onSubmit={handleAdminFileUpload} className="space-y-3 bg-neutral-900/40 border border-sp-bronze/25 rounded-lg p-4">
                  <div>
                    <label className="text-[10px] text-text-warm/40 uppercase mb-1 block">File</label>
                    <input
                      id="admin-file-upload-input"
                      type="file"
                      onChange={e => setAdminFileToUpload(e.target.files?.[0] || null)}
                      className="w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-3 py-2 file:mr-3 file:py-1 file:px-2 file:rounded file:border-0 file:bg-sp-gold file:text-black file:text-[10px] file:font-bold file:uppercase file:cursor-pointer cursor-pointer"
                    />
                  </div>
                  <div>
                    <label className="text-[10px] text-text-warm/40 uppercase mb-1 block">Description (optional)</label>
                    <input
                      className="w-full bg-neutral-950/60 border border-sp-bronze/30 text-white text-xs rounded-lg px-3 py-2 focus:outline-hidden focus:border-sp-gold"
                      value={adminFileDescription}
                      onChange={e => setAdminFileDescription(e.target.value)}
                      placeholder="e.g. Updated economy config, test before deploying"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={!adminFileToUpload || uploadingAdminFile}
                    className="flex items-center gap-1.5 px-4 py-2 bg-sp-gold hover:bg-white disabled:opacity-40 text-black text-[10px] font-bold uppercase tracking-wider rounded-lg transition-all cursor-pointer"
                  >
                    <Upload className="w-3.5 h-3.5" /> {uploadingAdminFile ? 'Uploading...' : 'Upload'}
                  </button>
                </form>

                <div className="space-y-2">
                  <h4 className="text-[10px] uppercase font-bold text-sp-gold/70">Shared Files ({adminFiles.length})</h4>
                  {loadingAdminFiles ? (
                    <p className="text-xs text-text-warm/40 italic">Loading...</p>
                  ) : adminFiles.length === 0 ? (
                    <p className="text-xs text-text-warm/40 italic">No files shared yet.</p>
                  ) : (
                    <div className="space-y-2">
                      {adminFiles.map(f => (
                        <div key={f.id} className="bg-neutral-900/30 border border-sp-bronze/20 rounded-lg p-3 flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <span className="text-xs font-bold text-white break-all">{f.originalName}</span>
                            {f.description && <p className="text-[10px] text-text-warm/60 mt-0.5">{f.description}</p>}
                            <p className="text-[9px] text-text-warm/40 mt-1">{formatFileSize(f.sizeBytes)} · uploaded by {f.uploadedBy} · {new Date(f.createdAt).toLocaleString()}</p>
                          </div>
                          <div className="flex gap-1.5 shrink-0">
                            <a
                              href={`/api/admin/files/download?id=${f.id}`}
                              className="p-1.5 bg-sp-bronze/25 hover:bg-sp-bronze border border-sp-bronze text-sp-gold hover:text-black rounded transition-all cursor-pointer"
                              title="Download"
                            >
                              <Download className="w-3.5 h-3.5" />
                            </a>
                            <button
                              onClick={() => handleAdminFileDelete(f.id, f.originalName)}
                              className="p-1.5 bg-crimson/15 hover:bg-crimson border border-crimson/45 text-rose-300 hover:text-white rounded transition-all cursor-pointer"
                              title="Delete"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

        </div>
      </div>

    </div>
  );
}

export default function ModerationPage() {
  return (
    <Suspense fallback={
      <div className="flex-1 flex items-center justify-center bg-bg-dark text-text-warm font-sans p-8">
        <div className="text-center space-y-3">
          <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-sp-gold mx-auto"></div>
          <p className="text-xs uppercase tracking-wider text-sp-gold/80 font-mono">Loading Command Center...</p>
        </div>
      </div>
    }>
      <ModerationContent />
    </Suspense>
  );
}
